(ns teet.backup.backup-ion
  "Backup Ion for exporting an .edn file backup to S3 bucket.

  When backing up the datomic we go through all the transactions that have happened to the db
  and write them to a file.

  Some of the transactions will be Datomic internal, which we can't transact again,
  these should be in the `ignore-attributes` set.
  "
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.integration.integration-s3 :as s3]
            [teet.integration.integration-context :as integration-context
             :refer [ctx-> defstep]]
            [clojure.java.io :as io]
            [teet.log :as log]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [clojure.set :as set])
  (:import (java.time.format DateTimeFormatter)))

(defn- current-iso-date []
  (.format (java.time.LocalDate/now) DateTimeFormatter/ISO_LOCAL_DATE))

(defn- read-seq
  "Lazy sequence of forms read from the given reader... don't let it escape with-open!"
  [rdr]
  (let [item (read rdr false ::eof)]
    (when (not= item ::eof)
      (lazy-seq
       (cons item
             (read-seq rdr))))))

(defstep download-backup-file
  {:doc "Download backup file from S3 to temporary directory. Puts file path to context"
   :in {backup-file {:spec ::s3/file-descriptor
                     :path-kw :backup-file
                     :default-path [:s3]}}
   :out {:spec string?
         :default-path [:file]}}
  (let [{:keys [bucket file-key]} backup-file
        file (java.io.File/createTempFile "backup" "edn" nil)]
    (log/info "Download backup file, bucket:  " bucket ", file-key: " file-key ", to local file: " file)
    (io/copy (s3/get-object bucket file-key)
             file)
    (.getAbsolutePath file)))

(defn- check-backup-format [{file :file :as ctx}]
  (with-open [istream (io/input-stream (io/file file))
              zip-in (java.util.zip.ZipInputStream. istream)]
    (let [entry (.getNextEntry zip-in)]
      (when (not= "transactions.edn" (.getName entry))
        (throw (ex-info "Invalid transaction format"
                        {:file-name (.getName entry)
                         :expected-file-name "transactions.edn"})))))
  ctx)

(defn- delete-backup-file [{file :file :as ctx}]
  (when (some? file)
        (.delete (io/file file)))
  (dissoc ctx :file))

(def ^{:private true
       :doc "Start of backup transaction. TEET started in 2019."}
  backup-start #inst "2019-01-01T00:00:00.000-00:00")

(defn- all-transactions
  "Returns all tx identifiers in time order.
  Skips the initial transactions empty databases have."
  [conn]
  (d/tx-range conn {:start backup-start
                    :limit -1}))

(defn- prepare-database-for-restore* [{:keys [datomic-client conn] :as ctx}
                                      connection-key
                                      create-database
                                      db-name clear-database?]
  (if create-database
    (do
      (log/info "CREATING NEW DATABASE: " create-database)
      (d/create-database datomic-client {:db-name create-database})
      (assoc ctx connection-key (d/connect datomic-client {:db-name create-database})))
    (if clear-database?
      (do
        (log/info "DELETING AND RECREATING " db-name " DATOMIC DATABASE.")
        (d/delete-database datomic-client {:db-name db-name})
        (d/create-database datomic-client {:db-name db-name})
        (assoc ctx connection-key (d/connect datomic-client {:db-name db-name})))
      (do
        (log/info "Using existing " db-name " database, checking that it is empty.")
        (when-not (empty? (all-transactions conn))
          (throw (ex-info "Database is not empty!"
                          {:transaction-count (count (all-transactions conn))})))
        ctx))))

(defn- prepare-database-for-restore [{:keys [config] :as ctx}]
  (prepare-database-for-restore* ctx
                                 :conn
                                 (:create-database config)
                                 (environment/db-name)
                                 (:clear-database? config)))

(defn- prepare-asset-db-for-restore [{:keys [config] :as ctx}]
  (if (environment/feature-enabled? :asset-db)
    (prepare-database-for-restore* ctx
                                   :asset-conn
                                   (:create-asset-database config)
                                   (environment/asset-db-name)
                                   (:clear-database? config))
    ctx))

(defn- read-restore-config [{event :event :as ctx}]
  (let [{:keys [file-key bucket] :as config*} (-> event :input (cheshire/decode keyword))
        config (dissoc config* :bucket :file-key)]
    (log/info "Read restore config from event:\n"
              "Restore from, bucket: " bucket ", file key: " file-key "\n"
              "Other options: " config)
    (if (or (str/blank? bucket)
            (str/blank? file-key))
      (throw (ex-info "Missing restore file configuration in event."
                      {:expected-keys [:file-key :bucket]
                       :got-keys (keys config*)}))
      (assoc ctx
             :s3 {:bucket bucket :file-key file-key}
             :config config))))

(defn- read-delete-config [{event :event :as ctx}]
  (let [{:keys [db-name asset-db-name] :as config*} (-> event :input (cheshire/decode keyword))
        config (dissoc config* :db-name :asset-db-name)]
    (log/info "Read delete config from event:\n"
      "Delete DB name: " db-name ", asset DB name: " asset-db-name "\n"
      "Other options: " config)
    (if (or (str/blank? db-name)
          (str/blank? asset-db-name))
      (throw (ex-info "Missing delete DB name(s)"
               {:expected-keys [:db-name :asset-db-name]
                :got-keys (keys config*)}))
      (assoc ctx
        :db-name db-name
        :asset-db-name asset-db-name
        :config config))))

(defn- attr-info [db attr-info-cache a]
  (get (swap! attr-info-cache
              (fn [attrs]
                (if (contains? attrs a)
                  attrs
                  (assoc attrs a (d/pull @db '[:db/ident :db/valueType] a)))))
       a))

(def ^:private ignore-attributes
  "Internal datomic stuff that we can skip"
  #{:db.install/attribute

    ; :db.alter/attribute added because of [:db/retract :thk.contract/procurement-id :db/unique :db.unique/identity]
    ; caused issues in the backup
    ; ERROR:
    ; {:cognitect.anomalies/category :cognitect.anomalies/incorrect,
    ; :cognitect.anomalies/message ":db.alter/attribute must be set on entity :db.part/db,
    ; found 79164837200314", :a 79164837200314, :e 79164837200314, :db/error :db.error/invalid-datom}
    :db.alter/attribute})


(defn- output-tx [db-ref attr-info-cache {:keys [data]} ignore-attributes]
  (let [datoms (for [{:keys [e a v added]} data
                     :let [{:db/keys [ident]}
                           (attr-info db-ref attr-info-cache a)

                           ;; Turn :db/* values into idents
                           v (if (and (str/starts-with? (str ident) ":db/")
                                      (integer? v))
                               (:db/ident (attr-info db-ref attr-info-cache v))
                               v)]
                     :when (not (ignore-attributes ident))]
                 [e ident v added])
        tx-id (:tx (first data))]
    {:tx (into {}
               (comp
                (filter (fn [[e _ _ _]]
                          (= e tx-id)))
                (map (fn [[_ a v _]]
                           [a v])))
               datoms)
     :data (vec (remove (fn [[e _ _ _]]
                          (= e tx-id))
                        datoms))}))

(defn- output-zip [ostream & entries]
  (with-open [zip-out (java.util.zip.ZipOutputStream. ostream)
              out (io/writer zip-out)]
    (doseq [[entry-name generate-entry-fn :as entry] entries
            :when entry]
      (.putNextEntry zip-out (java.util.zip.ZipEntry. entry-name))
      (generate-entry-fn out))))


;; Old ref attrs and tuple attrs, this is needed to skip old file-seen stuff that was later
;; aliased to entity-seen.
(def ^:private old-ref-attrs
  #{:file-seen/user :file-seen/file})
(def ^:private old-tuple-attrs
    {:file-seen/file+user [:file-seen/file :file-seen/user]})

(defn- output-all-tx [conn out]
  (let [db (d/db conn)
        attr-ident-cache (atom {})
        out! #(binding [*out* out] (prn %))
        ref-attrs (into old-ref-attrs
                        (comp
                         (map first)
                         (remove #(str/starts-with? (str %) ":db")))
                        (d/q '[:find ?id
                               :where
                               [?attr :db/ident ?id]
                               [?attr :db/valueType :db.type/ref]]
                             db))
        tuple-attrs (into old-tuple-attrs
                          (d/q '[:find ?id ?ta
                                 :where
                                 [?attr :db/ident ?id]
                                 [?attr :db/tupleAttrs ?ta]]
                               db))
        ignore-attributes (into ignore-attributes
                                (keys tuple-attrs))
        progress! (log/progress-fn "backup transactions written")]

    (out! {:ref-attrs ref-attrs
           :tuple-attrs tuple-attrs
           :backup-timestamp (java.util.Date.)})
    (doseq [tx (all-transactions conn)
            :let [tx-map (output-tx (delay (d/as-of db (:t tx))) attr-ident-cache tx
                                    ignore-attributes)]
            :when (and (seq (:data tx-map))
                       (.after (get-in tx-map [:tx :db/txInstant]) backup-start))]
      (out! tx-map)
      (progress!))))

(defn- prepare-restore-tx
  "Prepare transaction for restore.

  Takes tx datoms, old to new id mapping and set of reference attributes.
  Returns sequence of new datoms for the restore tx.

  Looks up entity ids and reference values from the old->new mapping."
  [tx-data old->new ref-attrs cardinality-many-attrs]
  (let [->id #(let [s (str %)]
                (or (old->new s) s))
        {card-many-datoms true
         card-one-datoms false} (group-by (comp boolean cardinality-many-attrs
                                                second)
                                          tx-data)]
    (concat
     ;; Output map tx for all cardinality one values, filtering out retractions
     ;; that have an assertion for the same attribute
     (mapcat (fn [[e datoms]]
               (let [e (->id e)

                     ;; Group by assertions and retractions
                     {asserted true
                      retracted false}
                     (group-by #(nth % 3) datoms)

                     asserted-map (when (seq asserted)
                                    (into {:db/id e}
                                          (map (fn [[_ a v _]]
                                                 [a (if (ref-attrs a)
                                                      (->id v) v)]))
                                          asserted))]
                 (into (if asserted-map
                         [asserted-map]
                         [])
                       (for [[_ a v _] retracted
                             :when (not (contains? asserted-map a))]
                         [:db/retract e a (if (ref-attrs a)
                                            (->id v) v)]))))
             (group-by first card-one-datoms))

     ;; Output add or retract clauses for any many cardinality values
     (for [[e a v add?] card-many-datoms
           :let [ref? (ref-attrs a)
                 e (->id e)
                 v (if ref?
                     (->id v)
                     v)]]
       [(if add? :db/add :db/retract) e a v]))))

(defn- add-cardinality-many-attrs!
  "Record :db/ident values of any new attributes created in tx-data"
  [set-atom tx-data]
  (let [card-many (into #{}
                        (keep (fn [tx]
                                (when (and (map? tx)
                                           (= :db.cardinality/many (:db/cardinality tx)))
                                  (:db/ident tx))))
                        tx-data)]
    (when (seq card-many)
      (swap! set-atom set/union card-many))))

(def retry-timeout-ms 60000)
(def retry-wait-ms 2000)
(def retryable-anomaly-categories #{:cognitect.anomalies/unavailable
                                    :cognitect.anomalies/interrupted
                                    :cognitect.anomalies/busy})
(defn- with-retry
  ([func]
   (with-retry (+ (System/currentTimeMillis)
                  retry-timeout-ms)
     func))
  ([give-up-at func]
   (loop []
     (let [[res e]
           (try
             [(func) nil]
             (catch Exception e
               [nil e]))]
       (if (nil? e)
         res
         (cond
           (> (System/currentTimeMillis) give-up-at)
           (throw (ex-info "Giving up after retry timed out"
                           {:exception e}))

           (some-> e ex-data
                   :cognitect.anomalies/category
                   retryable-anomaly-categories)
           (do
             (Thread/sleep retry-wait-ms)
             (recur))

           :else
           (throw (ex-info "Unretryable exception thrown"
                           {:exception e}))))))))

(defn- restore-tx-file*
  "Restore a backup by running the transactions in from the reader
  to the database pointed to by `conn`. It is assumed that
  the given database is empty.

  Returns the old->new id mapping."
  [conn rdr]
  ;; Read first form which is the mapping containing info about the backup
  (let [{:keys [backup-timestamp ref-attrs tuple-attrs]} (read rdr)

        ;; Initial set of card many attributes, tx processing will add any new ones here
        cardinality-many-attrs (atom (into #{}
                                           (map first)
                                           (d/q '[:find ?ident
                                                  :where
                                                  [?a :db/cardinality :db.cardinality/many]
                                                  [?a :db/ident ?ident]]
                                                (d/db conn))))
        progress! (log/progress-fn "transactions restored")]
    (assert (set? ref-attrs) "Expected set of :ref-attrs in 1st backup form")
    (assert (map? tuple-attrs) "Expected map of :tuple-attrs in 1st backup form")
    (assert (inst? backup-timestamp) "Expected :backup-timestamp in 1st backup form")
    (log/info "Restoring from tx log backup generated at: " backup-timestamp)
    (loop [old->new {}

           ;; Read rest of the forms (tx data) without retaining head
           txs (read-seq rdr)]
      (if-let [tx (first txs)]
        (let [tx-data (into [(merge (:tx tx)
                                    {:db/id "datomic.tx"})]
                            (prepare-restore-tx (:data tx)
                                                old->new
                                                ref-attrs
                                                @cardinality-many-attrs))
              {tempids :tempids}
              (with-retry
                #(d/transact
                  conn
                  {:tx-data tx-data}))]
          (add-cardinality-many-attrs! cardinality-many-attrs tx-data)
          (progress!)
          ;; Update old->new mapping with entity ids created in this tx
          (recur (merge old->new tempids)
                 (rest txs)))
        (do
          (log/info "All transactions applied.")
          old->new)))))

(defn- input-zip [file & entries]
  (with-open [istream (io/input-stream (io/file file))
              zip-in (java.util.zip.ZipInputStream. istream)
              zip-reader (io/reader zip-in)]

    (reduce
     (fn [result [entry-name process-entry-fn :as entry]]
       (if-not entry
         result
         (let [e (.getNextEntry zip-in)
               read-entry-name (some-> e .getName)
               rdr (java.io.PushbackReader. zip-reader)]
           (assert (= entry-name read-entry-name)
                   (str "Wrong zip entry, expected: " entry-name
                        ", got: " read-entry-name))
           (assoc result
                  entry-name
                  (process-entry-fn rdr)))))
     {} entries)))

(defn- restore-tx-file
  "Restore a TEET backups from zip `file`.
  Reads transactions.edn (TEET backup) and assets.edn (asset db backup
  if enabled).
  "
  [{:keys [conn asset-conn file] :as ctx}]

  (log/info "Restoring backup from file: " file)
  ;; read all users and transact them in one go
  (let [result
        (input-zip file
                   ["transactions.edn" #(restore-tx-file* conn %)]
                   (when (environment/feature-enabled? :asset-db)
                     ["assets.edn" #(restore-tx-file* asset-conn %)]))]
    (assoc ctx
           :old->new (result "transactions.edn")
           :asset-old->new (result "assets.edn"))))


(defn- write-backup-to-zip-file [conn file]
  (with-open [out (io/output-stream file)]
    (output-zip out
                ["transactions.edn" (partial output-all-tx conn)]
                (when (environment/feature-enabled? :asset-db)
                  ["assets.edn" (partial output-all-tx
                                         (environment/asset-connection))]))))

(defn- backup* [_event]
  (let [bucket (environment/config-value :backup :bucket-name)
        env (environment/config-value :env)
        conn (environment/datomic-connection)
        file-key (str env "-backup-"
                      (current-iso-date)
                      ".edn.zip")
        start-ms (System/currentTimeMillis)]
    (log/info "Starting TEET backup to bucket: "
              bucket ", file: " file-key)
    (try
      (let [file (java.io.File/createTempFile "backup" ".zip")]
        (log/info "Generating backup zip file: " (.getAbsolutePath file))
        (write-backup-to-zip-file conn file)
        (log/info "Backup generated in " (int (/ (- (System/currentTimeMillis) start-ms) 1000))
                  "seconds with size " (.length file)
                  ". Uploading to S3.")

        (with-open [in (io/input-stream file)]
          (s3/write-file-to-s3
           {:to {:bucket bucket
                 :file-key file-key}
            :contents in}))
        (io/delete-file file))
      (log/info "TEET backup finished.")
      (catch Exception e
        (log/error e "TEET backup failed")))))

(defn- migrate [{conn :conn :as ctx}]
  (environment/migrate conn @environment/schema)
  ctx)

(defn log-restore-result
  "Write log to bucket into the same file name from which the restore was started"
  [{{bucket :bucket file-key :file-key} :s3 error :error}]
  (try
    (let [file (java.io.File/createTempFile file-key ".log")]
      (log/info "Generating restore log file: " (.getAbsolutePath file))
      (with-open [w (io/writer file :append true)]
        (.write w
          (if (empty? error)
            "SUCCESS"
            (str "FAILURE"
              (:message error)))))
      (with-open [in (io/input-stream file)]
        (s3/write-file-to-s3
          {:to {:bucket bucket
                :file-key (str file-key ".log")}
           :contents in}))
      (io/delete-file file))
    (log/info "Backup restore log created.")
    (catch Exception e
      (log/error e "Backup restore logging failed"))))

(defn backup
  "Lambda function endpoint for backing up database as a transaction log to S3"
  [_event]
  (future
    (backup* _event))
  "{\"success\": true}")


(defn- restore* [event]
  (try
      (ctx-> {:event event
              :api-url (environment/config-value :api-url)
              :api-secret (environment/config-value :auth :jwt-secret)
              :datomic-client (environment/datomic-client)
              :conn (environment/datomic-connection)}
             read-restore-config
             download-backup-file
             check-backup-format
             prepare-database-for-restore
             prepare-asset-db-for-restore
             restore-tx-file
             migrate
             log-restore-result
             delete-backup-file)
      (catch Exception e
        (log/error e "ERROR IN RESTORE" (ex-data e))
        (log-restore-result (ex-data e)))))

(defn restore
  "Lambda function endpoint for restoring database from transaction log in S3"
  [event]
  (future
    (restore* event))
  "{\"success\": true}")

(defn- delete-datomic-dbs
  "Delete TEET DB and Asset DB."
  [{:keys [db-name asset-db-name datomic-client] :as ctx}]
  (println "CONTEXT: " ctx)
  (log/info "Delete backup DB: " db-name " and Asset DB: " asset-db-name " datomic-client " datomic-client)
  (d/delete-database datomic-client {:db-name db-name})
  (d/delete-database datomic-client {:db-name asset-db-name}))

(defn- delete-db* [event]
  (println "EVENT: " event)
  (try
    (ctx-> {:event event
            :datomic-client (d/client (environment/config-value :datomic :client))
            :conn (environment/datomic-connection)}
      read-delete-config
      delete-datomic-dbs)
    (catch Exception e
      (log/error e "ERROR IN DELETE" (ex-data e)))))

(defn delete-db
  "Lambda function endpoint for delete database and asset db"
  [event]
  (future
    (delete-db* event))
  "{\"success\": true}")


;; ion :delete-email-addresses-from-datomic entry point
;;
(defn delete-user-email-addresses [_]
  (let [db-connection (environment/datomic-connection)
        db (d/db db-connection)
        users-result (d/q '[:find ?u ?email :keys user-eid email
                            :where [?u :user/email ?email]]
                          db)
        retract-tx (vec (for [{:keys [user-eid email]} users-result]
                          [:db/retract user-eid :user/email email]))]
    (d/transact db-connection {:tx-data retract-tx})
    (log/info "Removed email address from" (count retract-tx) "users")
    "{\"success\": true}"))

(defn- debug-restore [new-db-name edn-file-path]
  (let [client (environment/datomic-client)]
    (d/create-database client {:db-name new-db-name})
    (restore-tx-file* (d/connect client {:db-name new-db-name})
                      (java.io.PushbackReader. (io/reader edn-file-path)))))

(comment
  (do
    (debug-restore
    "ttest8"
    "/Users/tatuta/temp/transactions.edn")
    :ok))
