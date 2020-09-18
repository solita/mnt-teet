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
            [clojure.walk :as walk]
            [clojure.set :as set]
            [teet.util.datomic :as du]
            [teet.log :as log]
            [clojure.string :as str]
            [teet.integration.postgrest :as postgrest]
            [cheshire.core :as cheshire])
  (:import (java.time LocalDate)
           (java.time.format DateTimeFormatter)))

(defn prepare [form]
  (walk/prewalk
   (fn [x]
     (cond
       (and (map? x) (contains? x :db/ident))
       (:db/ident x)

       :else
       x))
   form))

(defn pull-project [db id]
  (d/pull db '[*
               {:thk.project/lifecycles
                [*
                 {:thk.lifecycle/activities
                  [*
                   {:activity/tasks
                    [*
                     {:task/comments
                      [* {:comment/files [*]}]}
                     {:task/files
                      [*
                       {:file/comments
                        [*
                         {:comment/files [*]}]}]}]}]}]}] id))

(defn pull-user [db id]
  (d/pull db '[* {:user/permissions [*]}] id))

(defn pull* [db id]
  (d/pull db '[*] id))

(defn pull-estate-procedure [db id]
  (d/pull db '[*
               {:estate-procedure/third-party-compensations [*]}
               {:estate-procedure/land-exchanges [*]}
               {:estate-procedure/compensations [*]}
               {:estate-procedure/process-fees [*]}] id))

(defn query-ids-with-attr [db attr]
  (map first (d/q [:find '?e :where ['?e attr '_]] db)))

(defn referred-and-provided-ids
  "Walk form and return all map containing all ids, both referred and provided.
  A referred id is a map with just :db/id key.
  A provided id the :db/id of a map that contains other values as well"
  [form]
  (let [referred (volatile! #{})
        provided (volatile! #{})]
    (walk/prewalk
     (fn [f]
       (when (and (map? f)
                  (contains? f :db/id))

         (vswap! (if (= (set (keys f)) #{:db/id})
                   referred
                   provided)
                 conj (:db/id f)))
       f)
     form)
    {:referred @referred :provided @provided}))

(defn fetch-entities-to-backup [db]
  (map prepare
       (concat
        ;; Dump all users with
        [";; Dump all users"]
        (map (partial pull-user db)
             (query-ids-with-attr db :user/id))

        [";; Dump all user notifications"]
        (map (partial pull* db)
             (query-ids-with-attr db :notification/type))

        ;; Dump all projects with all nested data
        [";; Dump all projects"]
        (map (partial pull-project db)
             (query-ids-with-attr db :thk.project/id))

        [";; Dump all land acquisitions"]
        (map (partial pull* db)
             (query-ids-with-attr db :land-acquisition/cadastral-unit))

        [";; Dump all estate procedures"]
        (map (partial pull-estate-procedure db)
             (query-ids-with-attr db :estate-procedure/type)))))

(defn backup-to [db ostream]
  (with-open [zip-out (doto (java.util.zip.ZipOutputStream. ostream)
                        (.putNextEntry (java.util.zip.ZipEntry. "backup.edn")))
              out (io/writer zip-out)]
    (loop [referred #{}
           provided #{}
           [entity & entities] (fetch-entities-to-backup db)]
      (if-not entity
        (do
          (when (seq (set/difference referred provided))
            (let [missing-referred-ids (set/difference referred provided)
                  msg "Export referred to entities that were not provided! This backup is inconsistent."]
              (log/warn msg (set/difference referred provided))
              (throw (ex-info msg {:missing-referred-ids missing-referred-ids}))))
          {:backed-up-entity-count (count provided)})
        (if (string? entity)
          (do
            (.write out entity)
            (.write out "\n")
            (recur referred provided entities))

          (let [ids (referred-and-provided-ids entity)]
            (pprint/pprint entity out)
            (.write out "\n")
            (recur (set/union referred (:referred ids))
                   (set/union provided (:provided ids))
                   entities)))))))

(defn current-iso-date []
  (.format (java.time.LocalDate/now) DateTimeFormatter/ISO_LOCAL_DATE))

(defn backup* [_event]
  (let [bucket (environment/ssm-param :s3 :backup-bucket)
        env (environment/ssm-param :env)
        db (d/db (environment/datomic-connection))]
      (s3/write-file-to-s3 {:to {:bucket bucket
                                 :file-key (str env "-backup-"
                                                (current-iso-date)
                                                ".edn.zip")}
                            :contents (ring-io/piped-input-stream (partial backup-to db))})))

(defn backup [_event]
  (future
    backup* _event)
  "{\"success\": true}")

(defn test-backup [db]
  (with-open [out (io/output-stream (io/file "backup.edn.zip"))]
    (backup-to db out)))

(defn to-temp-ids [form]
  (let [mapping (volatile! {})
        form (walk/prewalk
              (fn [f]
                (if (and (map? f) (contains? f :db/id))
                  (let [id (:db/id f)]
                    (vswap! mapping assoc (str id) id)
                    (update f :db/id str))
                  f))
              form)]
    {:mapping @mapping
     :form form}))

(defn read-seq
  "Lazy sequence of forms read from the given reader... don't let it escape with-open!"
  [rdr]
  (let [item (read rdr false ::eof)]
    (when (not= item ::eof)
      (lazy-seq
       (cons item
             (read-seq rdr))))))

(defn check-ids-without-attributes [form]
  (let [ids (volatile! (du/db-ids form))]
    (walk/prewalk
     (fn [x]
       ;; This is an entity map that has more than just the :db/id
       (when-let [id (and (map? x) (:db/id x))]
         (when (> (count (keys x)) 1)
           (vswap! ids disj id)))
       x) form)
    @ids))

(defn s3-filename [db-id filename]
  (str db-id "-" filename))

(defn query-all-files! [conn]
  (map first
       (d/q '[:find (pull ?f [:db/id :file/name])
              :in $
              :where [?f :file/name _]]
            (d/db conn))))

;; Match and replace mentions like "@[user name](user db id)"
;; with new user ids
(defn replace-comment-mention-ids [txt tempids]
  (str/replace txt
               #"@\[([^\]]+)\]\((\d+)\)"
               (fn [[_ name id]]
                 (str "@[" name "](" (tempids id) ")"))))

;; "hei @[Benjamin Boss](92358976733956) mitä kuuluu?"
(defn rewrite-comment-mentions! [{:keys [conn tempids] :as ctx}]
  (let [comments (map first
                      (d/q '[:find (pull ?c [:db/id :comment/comment])
                             :where
                             [?c :comment/comment ?txt]
                             [(clojure.string/includes? ?txt "@[")]]
                           (d/db conn)))]
    (log/info "Rewriting " (count comments) " with user mentions")
    (d/transact conn
                {:tx-data (vec (for [{id :db/id txt :comment/comment} comments
                                     :let [new-txt (replace-comment-mention-ids txt tempids)]]
                                 {:db/id id
                                  :comment/comment new-txt}))})
    (assoc ctx :comments-with-mentions-rewritten (count comments))))


(defn replace-entity-ids!
  "Replace entity ids in postgres entity table. List of ids
  is comma separated list of old_id=new_id."
  [{:keys [tempids] :as ctx}]
  (log/info "Replace " (count tempids) " entity ids in PostgREST")
  (postgrest/rpc ctx :replace_entity_ids
                 {:idlist (str/join ","
                                    (map (fn [[old-id new-id]]
                                           (str old-id "=" new-id))
                                         tempids))})
  ctx)

(defn restore-file
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
    ;; Transact everything in one big transaction
    (let [form (doall (read-seq rdr))
                                        ;ids (check-ids-without-attributes form)
          _ (log/info "Read " (count form) " forms from backup.")
          {:keys [mapping form]} (to-temp-ids form)
          {tempids :tempids} (d/transact conn {:tx-data (vec form)}) ]
      (log/info "Restore transaction applied.")
      (merge ctx {:mapping mapping :form form :tempids tempids}))))

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

(defn delete-backup-file [{file :file :as ctx}]
  (.delete (io/file file))
  (dissoc ctx :file))

(defn prepare-database-for-restore [{:keys [datomic-client conn config] :as ctx}]
  (if (:clear-database? config)
    (do
      (log/info "DELETING AND RECREATING \"teet\" DATOMIC DATABASE.")
      (d/delete-database datomic-client {:db-name "teet"})
      (d/create-database datomic-client {:db-name "teet"})
      (let [conn (d/connect datomic-client {:db-name "teet"})]
        (environment/migrate conn)
        (assoc ctx :conn conn)))
    (do
      (log/info "Using existing \"teet\" database, checking that it is empty.")
      (let [projects (d/q '[:find ?e :where [?e :thk.project/id _]]
                          (d/db conn))]
        (when (seq projects)
          (throw (ex-info "Database is not empty!"
                          {:found-projects (count projects)})))
        ctx))))

(defn read-restore-config [{event :event :as ctx}]
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


(defn restore* [event]
  (try
      (ctx-> {:event event
              :api-url (environment/config-value :api-url)
              :api-secret (environment/config-value :auth :jwt-secret)
              :datomic-client (environment/datomic-client)
              :conn (environment/datomic-connection)}
             read-restore-config
             download-backup-file
             prepare-database-for-restore
             restore-file
             rewrite-comment-mentions!
             replace-entity-ids!
             delete-backup-file)
      (catch Exception e
        (log/error e "ERROR IN RESTORE" (ex-data e)))))

(defn restore [event]
  (future
    (restore* event))
  "{\"success\": true}")

(defn- all-transactions
  "Returns all tx identifiers in time order.
  Skips the initial transactions empty databases have."
  [conn]
  (d/tx-range conn {:start (java.util.Date. 1)}))

(defn- attr-info [db attr-info-cache a]
  (get (swap! attr-info-cache
              (fn [attrs]
                (if (contains? attrs a)
                  attrs
                  (assoc attrs a (d/pull db '[:db/ident :db/valueType] a)))))
       a))

(defn output-tx [db attr-info-cache {:keys [data]}]
  (let [datoms (for [{:keys [e a v added]} data
                     :let [{:db/keys [ident]}
                           (attr-info db attr-info-cache a)]]
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

(defn output-all-tx [conn ostream]
  (with-open [zip-out (doto (java.util.zip.ZipOutputStream. ostream)
                        (.putNextEntry (java.util.zip.ZipEntry. "backup.edn")))
              out (io/writer zip-out)]
    (let [db (d/db conn)
          attr-ident-cache (atom {})
          out! #(pprint/pprint % out)]
      (out! {:ref-attributes
             (into #{}
                   (map first)
                   (d/q '[:find ?id
                          :where
                          [?attr :db/ident ?id]
                          [?attr :db/valueType :db.type/ref]]
                        db))
             :tuple-attrs
             (into {}
                   (d/q '[:find ?id ?ta
                          :where
                          [?attr :db/ident ?id]
                          [?attr :db/tupleAttrs ?ta]]
                        db))
             :backup-timestamp (java.util.Date.)})
      (doseq [tx (all-transactions conn)]
        (out! (output-tx db attr-ident-cache tx))))))

;; need to know ref types
;;
;; need to know tupleattrs
;;
