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
            [teet.integration.postgrest :as postgrest])
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
        [";; Dump all users"]
        ;; seems dev env can have mock users without user/id, we don't want to
        ;; trigger incomplete backup error from that
        (map (partial pull-user db)
             (seq (into #{}
                        (concat (query-ids-with-attr (user/db) :user/id)
                                (query-ids-with-attr (user/db) :user/person-id)))))

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

(defn disable-zip [ostream zip-out]
  ;; hack to render incomplete backup zip unreadable
  ;; (we try to delete them but things could fail, and the s3 streaming write can't be canceled)
  (.finish zip-out)
  (.write ostream (byte-array (repeat 100000 42)))
  (.close zip-out)
  (.close ostream))

(defn backup-to [db status-atom ostream]
  ;; Close-safety to catch buffered write errors:
  ;; 1. This is assumed to be called by ring-io/piped-input-stream which promises
  ;;     to .close the ostream.
  ;; 2. The ZipOutputstream and its io/writer will be closed
  ;;    by the with-open form
  (with-open [zip-out (doto (java.util.zip.ZipOutputStream. ostream)
                        (.putNextEntry (java.util.zip.ZipEntry. "backup.edn")))
              out (io/writer zip-out)]
    ;; loop over entities to backup,
    ;; carrying sets of referred and provided as loop state
    (loop [referred #{}
           provided #{}
           [entity & entities] (fetch-entities-to-backup db)]
      (if-not entity
        (do
          (if (seq (set/difference referred provided))
            (let [missing-referred-ids (set/difference referred provided)
                  msg "Export referred to entities that were not provided! This backup is inconsistent."]
              (log/error msg (set/difference referred provided))
              (.flush out)
              (disable-zip ostream zip-out)              
              (reset! status-atom :fail)
              (throw (ex-info msg {:missing-referred-ids missing-referred-ids}))))
          ;; with-open will close zip-out and out
          (do
            (reset! status-atom :ok)
            {:backed-up-entity-count (count provided)}))
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

(defn now-date-as-iso []
  (.format (java.time.LocalDate/now) DateTimeFormatter/ISO_LOCAL_DATE))

(defn backup [_event]
  (future
    (let [bucket (environment/ssm-param :s3 :backup-bucket)
          env (environment/ssm-param :env)
          db (d/db (environment/datomic-connection))
          status-atom (atom nil) ;; exceptions don't seem to make it through ring-io/piped-input-stream seems
          file-key (str env "-backup-" (now-date-as-iso) ".edn.zip")
          delete-failed-file! (fn []
                                (log/info "deleting failed backup file from s3:" file-key)
                                (s3/delete-object bucket file-key))]
      (try        
        (s3/write-file-to-s3 {:to {:bucket bucket
                                   :file-key file-key}
                              :contents (ring-io/piped-input-stream
                                         (partial backup-to db status-atom))})
        (catch clojure.lang.ExceptionInfo e          
          (log/error "caught exception writing backup:" (ex-data e))
          (delete-failed-file!)
          (reset! status-atom nil)))
      (when (= :fail @status-atom)
        (delete-failed-file!))))
  "{\"success\": true}")

(defn test-backup [db]

  (with-open [out (io/output-stream (io/file "backup.edn.zip"))]
    (backup-to db (atom nil) out)))

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

(defn validate-files-exist! [conn document-bucket tempids]
  (let [files (query-all-files! conn)]
    (doseq [file files]
      (let [s3-fn (s3-filename (tempids (:db/id file)) (:file/name file))]
        
        (log/debug "validating" s3-filename)
        (when-not (s3/object-exists? document-bucket s3-fn)
          (throw (ex-info "Missing file when performing restore sanity check, rename already done or bad documents bucket config?" {:missing-s3-file s3-fn})))))
    (log/info "validated that all" (count files) "files existed in s3 bucket" document-bucket)))

(defn rename-documents! [{:keys [conn document-bucket tempids]}]
  (validate-files-exist! conn document-bucket tempids)
  (let [files (query-all-files! conn)]
    (doseq [file files]
      (try
        (let [old-name (s3-filename (tempids (:db/id file))
                                    (:file/name file))
              new-name (s3-filename (:db/id file)
                                    (:file/name file))]
          ;; if the ids and hence the names are same, this will throw an InvalidRequest exception - to support restoring to the same instance and bucket instead of a blank one this would need ot be changed          
          (s3/rename-object document-bucket old-name new-name))))))

;; Match and replace mentions like "@[user name](user db id)"
;; with new user ids
(defn replace-comment-mention-ids [txt tempids]
  (str/replace txt
               #"@\[([^\]]+)\]\((\d+)\)"
               (fn [[_ name id]]
                 (str "@[" name "](" (tempids id) ")"))))

;; "hei @[Benjamin Boss](92358976733956) mitä kuuluu?"
(defn rewrite-comment-mentions! [{:keys [conn tempids]}]
  (let [comments (map first
                      (d/q '[:find (pull ?c [:db/id :comment/comment])
                             :where
                             [?c :comment/comment ?txt]
                             [(clojure.string/includes? ?txt "@[")]]
                           (d/db conn)))]
    (d/transact conn
                {:tx-data (vec (for [{id :db/id txt :comment/comment} comments
                                     :let [new-txt (replace-comment-mention-ids txt tempids)]]
                                 {:db/id id
                                  :comment/comment new-txt}))})))


(defn replace-entity-ids!
  "Replace entity ids in postgres entity table. List of ids
  is comma separated list of old_id=new_id."
  [{:keys [tempids] :as ctx}]
  (postgrest/rpc ctx :replace_entity_ids
                 {:idlist (str/join ","
                                    (map (fn [[old-id new-id]]
                                           (str old-id "=" new-id))
                                         tempids))}))

(defn restore-file
  "Restore a TEET backup zip from `file` by running the transactions in
  the file to the database pointed to by `conn`. It is assumed that
  the given database is empty."
  [{:keys [conn file] :as ctx}]

  ;; read all users and transact them in one go
  (with-open [istream (io/input-stream (io/file file))
              zip-in (doto (java.util.zip.ZipInputStream. istream)
                       (.getNextEntry))
              zip-reader (io/reader zip-in)
              rdr (java.io.PushbackReader. zip-reader)]
    ;; Transact everything in one big transaction
    (let [form (doall (read-seq rdr))
                                        ;ids (check-ids-without-attributes form)
          {:keys [mapping form]} (to-temp-ids form)
          {tempids :tempids} (d/transact conn {:tx-data (vec form)})
          ctx (merge ctx {:mapping mapping :form form :tempids tempids})]
      (rename-documents! ctx)
      (rewrite-comment-mentions! ctx)
      (replace-entity-ids! ctx))))

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

(defn validate-empty-environment [{conn :conn :as ctx}]
  (let [projects (d/q '[:find ?e :where [?e :thk.project/id _]]
                      (d/db conn))]
    (when (seq projects)
      (throw (ex-info "Database is not empty!"
                      {:found-projects (count projects)})))
    ctx))


(defn restore [event]
  (future
    (ctx-> {:event event
            :api-url (environment/config-value :api-url)
            :api-secret (environment/config-value :auth :jwt-secret)
            :conn (environment/datomic-connection)}
           s3/read-trigger-event
           download-backup-file
           validate-empty-environment
           restore-file
           delete-backup-file))
  "{\"success\": true}")

