(ns teet.backup.backup-ion
  "Backup Ion for exporting an .edn file backup to S3 bucket."
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.integration.integration-s3 :as s3]
            [teet.integration.integration-context :as integration-context
             :refer [ctx-> defstep]]
            [clojure.java.io :as io]
            [teet.log :as log]
            [clojure.string :as str]
            [cheshire.core :as cheshire])
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
  (.delete (io/file file))
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

(defn- attr-info [db attr-info-cache a]
  (get (swap! attr-info-cache
              (fn [attrs]
                (if (contains? attrs a)
                  attrs
                  (assoc attrs a (d/pull @db '[:db/ident :db/valueType] a)))))
       a))

(def ^:private ignore-attributes
  "Internal datomic stuff that we can skip"
  #{:db.install/attribute})


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

(defn- progress-fn [message]
  (let [prg (atom 0)]
    (fn []
      (let [p (swap! prg inc)]
        (when (zero? (mod p 500))
          (log/info p message))))))

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
        progress! (progress-fn "backup transactions written")]

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
  [tx-data old->new ref-attrs]
  (let [->id #(let [s (str %)]
                (or (old->new s) s))]
    (for [[e a v add?] tx-data
          :let [ref? (ref-attrs a)
                e (->id e)
                v (if ref?
                    (->id v)
                    v)]]
      [(if add? :db/add :db/retract) e a v])))


(defn- restore-tx-file*
  "Restore a backup by running the transactions in from the reader
  to the database pointed to by `conn`. It is assumed that
  the given database is empty.

  Returns the old->new id mapping."
  [conn rdr]
  ;; Read first form which is the mapping containing info about the backup
  (let [{:keys [backup-timestamp ref-attrs tuple-attrs]} (read rdr)]
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
                                                ref-attrs))
              {tempids :tempids}
              (d/transact
               conn
               {:tx-data tx-data})]

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
  [{{bucket :bucket file-key :file-key} :s3}]
  (try
    (let [file (java.io.File/createTempFile file-key ".log")]
      (log/info "Generating restore log file: " (.getAbsolutePath file))
      (with-open [w (io/writer file :append true)]
                 (.write w (str "Backup " file-key " was restored successfully.")))
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
        (log/error e "ERROR IN RESTORE" (ex-data e)))))

(defn restore
  "Lambda function endpoint for restoring database from transaction log in S3"
  [event]
  (future
    (restore* event))
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
