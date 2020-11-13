(ns teet.backup.backup-ion
  "Backup Ion for exporting an .edn file backup to S3 bucket."
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.integration.integration-s3 :as s3]
            [teet.integration.integration-context :as integration-context
             :refer [ctx-> defstep]]
            [ring.util.io :as ring-io]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [teet.util.datomic :as du]
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

(defn- all-transactions
  "Returns all tx identifiers in time order.
  Skips the initial transactions empty databases have."
  [conn]
  (d/tx-range conn {:start #inst "2019-01-01T00:00:00.000-00:00"
                    :limit -1}))

(defn- prepare-database-for-restore [{:keys [datomic-client conn config] :as ctx}]
  (if-let [create-database (:create-database config)]
    (do
      (log/info "CREATING NEW DATABASE: " create-database)
      (d/create-database datomic-client {:db-name create-database})
      (assoc ctx :conn (d/connect datomic-client {:db-name create-database})))
    (let [db-name (environment/db-name)]
      (if (:clear-database? config)
        (do
          (log/info "DELETING AND RECREATING " db-name " DATOMIC DATABASE.")
          (d/delete-database datomic-client {:db-name db-name})
          (d/create-database datomic-client {:db-name db-name})
          (assoc ctx :conn (d/connect datomic-client {:db-name db-name})))
        (do
          (log/info "Using existing " db-name " database, checking that it is empty.")
          (when-not (empty? (all-transactions conn))
            (throw (ex-info "Database is not empty!"
                            {:transaction-count (count (all-transactions conn))})))
          ctx)))))

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
                  (assoc attrs a (d/pull db '[:db/ident :db/valueType] a)))))
       a))

(def ^:private ignore-attributes
  "Internal datomic stuff that we can skip"
  #{:db.install/attribute})

(defn- output-tx [db attr-info-cache {:keys [data]} ignore-attributes]
  (let [datoms (for [{:keys [e a v added]} data
                     :let [{:db/keys [ident]}
                           (attr-info db attr-info-cache a)

                           ;; Turn :db/* values into idents
                           v (if (and (str/starts-with? (str ident) ":db/")
                                      (integer? v))
                               (:db/ident (du/entity db v))
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

(defn- output-all-tx [conn ostream]
  (with-open [zip-out (doto (java.util.zip.ZipOutputStream. ostream)
                        (.putNextEntry (java.util.zip.ZipEntry. "transactions.edn")))
              out (io/writer zip-out)]
    (let [db (d/db conn)
          attr-ident-cache (atom {})
          out! #(pprint/pprint % out)
          ref-attrs (into #{}
                          (comp
                           (map first)
                           (remove #(str/starts-with? (str %) ":db")))
                          (d/q '[:find ?id
                                 :where
                                 [?attr :db/ident ?id]
                                 [?attr :db/valueType :db.type/ref]]
                               db))
          tuple-attrs (into {}
                            (d/q '[:find ?id ?ta
                                   :where
                                   [?attr :db/ident ?id]
                                   [?attr :db/tupleAttrs ?ta]]
                                 db))
          ignore-attributes (into ignore-attributes
                                  (keys tuple-attrs))]

      (out! {:ref-attrs ref-attrs
             :tuple-attrs tuple-attrs
             :backup-timestamp (java.util.Date.)})
      (doseq [tx (all-transactions conn)]
        (out! (output-tx db attr-ident-cache tx
                         ignore-attributes))))))

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


(defn- restore-tx-file
  "Restore a TEET backup zip from `file` by running the transactions in
  the file to the database pointed to by `conn`. It is assumed that
  the given database is empty."
  [{:keys [conn file] :as ctx}]

  (log/info "Restoring backup from file: " file)
  ;; read all users and transact them in one go
  (with-open [istream (io/input-stream (io/file file))
              zip-in (doto (java.util.zip.ZipInputStream. istream)
                       (.getNextEntry))
              zip-reader (io/reader zip-in)
              rdr (java.io.PushbackReader. zip-reader)]
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
            (assoc ctx :old->new old->new)))))))

(comment
  (output-all-tx (environment/datomic-connection)
                 (io/output-stream
                  (io/file "backup-tx.edn.zip"))))

(defn- backup* [_event]
  (let [bucket (environment/config-value :backup :bucket-name)
        env (environment/config-value :env)
        conn (environment/datomic-connection)
        file-key (str env "-backup-"
                      (current-iso-date)
                      ".edn.zip")]
    (log/info "Starting TEET backup to bucket: "
              bucket ", file: " file-key)
    (try
      (s3/write-file-to-s3 {:to {:bucket bucket
                                 :file-key file-key}
                            :contents (ring-io/piped-input-stream
                                       (partial output-all-tx conn))})
      (log/info "TEET backup finished.")
      (catch Exception e
        (log/error e "TEET backup failed")))))

(defn- migrate [{conn :conn :as ctx}]
  (environment/migrate conn)
  ctx)

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
             restore-tx-file
             migrate
             delete-backup-file)
      (catch Exception e
        (log/error e "ERROR IN RESTORE" (ex-data e)))))

(defn restore
  "Lambda function endpoint for restoring database from transaction log in S3"
  [event]
  (future
    (restore* event))
  "{\"success\": true}")
