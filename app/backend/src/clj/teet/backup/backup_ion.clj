(ns teet.backup.backup-ion
  "Backup Ion for exporting an .edn file backup to S3 bucket."
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.integration.integration-s3 :as s3]
            [ring.util.io :as ring-io]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.walk :as walk]
            [clojure.set :as set]
            [teet.util.datomic :as du]
            [teet.log :as log]))

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

(defn backup [_event]
  (let [bucket (environment/ssm-param :s3 :backup-bucket)
        env (environment/ssm-param :env)
        db (d/db (environment/datomic-connection))]
    (s3/write-file-to-s3 {:to {:bucket bucket
                               :file-key (str env "-backup-"
                                              (java.util.Date.)
                                              ".edn.zip")}
                          :contents (ring-io/piped-input-stream (partial backup-to db))})))

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

(defn restore
  "Restore from REPL to empty database."
  [conn file]

  ;; read all users and transact them in one go
  (with-open [istream (io/input-stream (io/file file))
              zip-in (doto (java.util.zip.ZipInputStream. istream)
                       (.getNextEntry))
              zip-reader (io/reader zip-in)
              rdr (java.io.PushbackReader. zip-reader)]
    ;; Transact everything in one big transaction
    (let [form (doall (read-seq rdr))
          ids (check-ids-without-attributes form)
          _ (def *ids ids)
          {:keys [mapping form]} (to-temp-ids form)
          {tempids :tempids} (d/transact conn {:tx-data (vec form)})]
      {:mapping mapping :form form :tempids tempids})))
