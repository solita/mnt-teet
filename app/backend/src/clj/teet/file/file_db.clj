(ns teet.file.file-db
  "File queries"
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.comment.comment-db :as comment-db]
            [clj-time.core :as time]
            [clj-time.coerce :as tc]
            [teet.log :as log]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.util.datomic :as du]
            [teet.file.file-model :as file-model]))

(defn- valid-attach-definition? [attach]
  (and (vector? attach)
       (= 2 (count attach))
       (keyword? (first attach))))

(defn check-attach-definition [attach]
  (if-not (valid-attach-definition? attach)
    (throw (ex-info "Invalid attach definition"
                    {:invalid-attach-definition attach}))
    attach))

(defmulti attach-to
  "Check preconditions and permissions for attaching file to
  entity of given type and id for the user.

  Returna a map containing :eid to attach to if attaching is allowed, nil if not.
  May also throw an exception with :error key in the exception.

  Return map may also contain :wrap-tx function which will be used to process
  the tx-data vector before invoking the transaction.

  Default behaviour is to disallow."
  (fn [_db _user _file attach]
    (first (check-attach-definition attach))))

(defmulti allow-download-attachments?
  "Check preconditions and permissions for downloading files attached
  to given entity.

  Default behaviour is to disallow."
  (fn [_db _user attach]
    (first (check-attach-definition attach))))

(defmulti delete-attachment
  "Create transaction data for deleting a file attachment.
  Must check preconditions and permissions for deleting the attached file
  and throw exception with :error key in the data on failure.

  This is used to implement custom permission checks in different kinds of meeting attachments.
  When permission check passes, they all make the same kind of deletion-tx for the file entity.

  Default behaviour is to disallow and throw an exception.

  Dispatches on the on the first element of the \"attach\" parameter, which is a keyword-id pair such as [:meeting-decision id].

  The implementations emit the same kind of file delete tx, just their permission checks differ."
  (fn [_db _user _file-id attach]
    (first (check-attach-definition attach))))

(defmethod attach-to :default [_db user _file [entity-type entity-id]]
  (log/info "Disallow attaching file to " entity-type " with id " entity-id
            " for user " user)
  nil)

(defmethod allow-download-attachments? :default
  [_db user [entity-type entity-id]]
  (log/info "Disallow downloading attachments to " entity-type
            " with id " entity-id " for user " user))

(defmethod delete-attachment :default
  [_db user file-id [entity-type entity-id]]
  (log/info "Disallow deleting attachment " file-id " to "
            entity-type " with id " entity-id " for user " user)
  (throw (ex-info "Delete attachment not allowed"
                  {:error :not-allowed})))

(defn file-is-attached-to? [db file-id attach]
  (and (valid-attach-definition? attach)
       (boolean
        (seq
         (d/q '[:find ?eid
                :where [?f :file/attached-to ?eid]
                :in $ ?f ?eid]
              db file-id (second attach))))))

(defn own-file? [db user file-id]
  (boolean
   (ffirst
    (d/q '[:find ?f
           :where
           [?f :file/name _]
           [?f :meta/creator ?user]
           :in $ ?user ?f]
         db [:user/id (:user/id user)] file-id))))

(defn file-is-attached-to-comment? [db file-id comment-id]
  (boolean
   (ffirst
    (d/q '[:find ?f
           :where [?c :comment/files ?f]
           :in $ ?f ?c]
         db file-id comment-id))))

(defn is-key-user-file? [db file-id employee-id]
  (boolean
    (ffirst
      (d/q '[:find ?f
             :where [?e :company-contract-employee/attached-files ?f]
             :in $ ?f ?e]
           db file-id employee-id))))

(defn files-seen-at
  "Return mapping of file-id to timestamp when the given user has seen a given
  file. If the user has not seen a file, the file-id will not have a mapping."
  [db user file-ids]
  (into {}
        (d/q '[:find ?f ?at
               :where
               [?fs :file-seen/file ?f]
               [?fs :file-seen/user ?user]
               [?fs :file-seen/seen-at ?at]
               :in $ ?user [?f ...]]
             db (user-model/user-ref user) file-ids)))


(defn file-ids-with-comment-counts
  [db file-ids user]
  (reduce
    (fn [val id]
      (merge val {id (comment-db/comment-count-of-entity-by-status db user id :file)}))
    {}
    file-ids))

(defn file-metadata
  "Return file metadata for a fetched file entity."
  [{:file/keys [name sequence-number] :as file}]
  (merge
    (filename-metadata/name->description-and-extension name)
    (when-let [p (get-in file [:task/_files 0 :activity/_tasks 0 :thk.lifecycle/_activities 0 :thk.project/_lifecycles 0 :thk.project/id])]
      {:thk.project/id p})
    (when sequence-number
      {:sequence-number sequence-number})
    (when-let [dg (get-in file [:file/document-group :filename/code])]
      {:document-group dg})
    (when-let [act (get-in file [:task/_files 0 :activity/_tasks 0 :activity/name :filename/code])]
      {:activity act})
    (when-let [task (get-in file [:task/_files 0 :task/type :filename/code])]
      {:task task})
    {:part (or (some->> file :file/part :file.part/number
                        (format "%02d"))
               "00")}))

(defn pull-file-metadata-by-id
  "Recursively pull all information from file that is needed for determining file metadata."
  [db file-id]
  (d/pull db [:file/name
              :file/sequence-number
              {:file/document-group [:filename/code]}
              {:task/_files [:db/id
                             {:task/type [:filename/code]}
                             {:activity/_tasks
                              [{:activity/name [:filename/code]}
                               {:thk.lifecycle/_activities
                                [{:thk.project/_lifecycles
                                  [:thk.project/id]}]}]}]}
              {:file/part [:file.part/number]}] file-id))

(defn is-task-file?
  "Differentiate between attachments and other stored files vs task file uploads"
  [db file-eid]
  (->> file-eid
       (pull-file-metadata-by-id db)
       :task/_files
       first
       :activity/_tasks
       not-empty))

(defn file-metadata-by-id
  "Return file metadata for a given file id."
  [db file-id]
  (file-metadata
    (pull-file-metadata-by-id db file-id)))

(defn file-listing-info
  "Fetch file listing info for the set of file ids, returns info for all
  files without grouping them by version."
  [db user file-ids]
  (let [;; File seen statuses
        seen-at-by-file (files-seen-at db user file-ids)

        ;; Get comment counts for files
        comment-counts-by-file
        (file-ids-with-comment-counts db file-ids user)]


    (mapv
     (comp #(merge %
                   (comment-counts-by-file (:db/id %))
                   {:file-seen/seen-at (seen-at-by-file (:db/id %))
                    :file/full-name (filename-metadata/metadata->filename
                                      (file-metadata-by-id db (:db/id %)))})
           first)
     (d/q '[:find (pull ?f file-listing-attributes)
            :where
            [?f :file/upload-complete? true]
            :in $ file-listing-attributes [?f ...]]
          db
          file-model/file-listing-attributes
          file-ids))))

(defn file-listing
  "Fetch file information suitable for file listing. Returns all file attributes
  and owner info. Returns only the latest version of each file with previous versions
  as :versions key unless latest-only? provided as true.

  Includes the timestamp user has seen the file (if any) as :file-seen/seen-at."
  [db user file-ids]
  (let [files (file-listing-info db user file-ids)

        ;; Group files to first versions and next versions
        {next-versions  true
         first-versions false}
        (group-by (comp boolean :file/previous-version) files)

        ;; Find next version for given file
        next-version (fn [file]
                       (some #(when (= (:db/id file)
                                       (get-in % [:file/previous-version :db/id])) %)
                             next-versions))]
    (vec
      (sort-by :file/name
               (for [f first-versions
                     :let [versions (filter (complement :meta/deleted?)
                                            (reverse
                                              (take-while some? (iterate next-version f))))
                           [latest-version & previous-versions] versions]
                     :when latest-version]
                 (assoc latest-version
                   :file/full-name (filename-metadata/metadata->filename
                                     (file-metadata-by-id db (:db/id latest-version)))
                   :versions (when (seq previous-versions) previous-versions)))))))

(defn latest-file-listing
  "Fetch file information suitable for file listing. Returns all file attributes
  and owner info. Assumes only the latest version of each file are given in parameters.
  Includes the timestamp user has seen the file (if any) as :file-seen/seen-at."
  [db user latest-file-ids]
  (let [files (file-listing-info db user latest-file-ids)

        ;; Find next version for given file
        next-version (fn [file]
                       (some #(when (= (:db/id file)
                                      (get-in % [:file/previous-version :db/id])) %)
                         files))]
    (vec
      (sort-by :file/name
        (for [f files
              :let [versions (iterate next-version f)
                    [latest-version & _] versions]
              :when latest-version]
          (assoc latest-version
            :file/full-name (filename-metadata/metadata->filename
                              (file-metadata-by-id db (:db/id latest-version)))
            :versions []))))))


(defn land-files-by-project-and-sequence-number [db user project-id sequence-number]
  (file-listing
   db user
   (mapv first
         (d/q '[:find ?f
                :where
                ;; File has this sequence number
                [?f :file/sequence-number ?seqno]

                ;; File belongs to the project
                [?task :task/files ?f]
                ;; Earlier it was possible to delete a task without deleting the files first.
                ;; We don't want to list the files whose task has been deleted.
                [(missing? $ ?task :meta/deleted?)]
                ;; Or that haven't finished uploading
                [?f :file/upload-complete? true]
                [?act :activity/tasks ?task]
                [?act :activity/name :activity.name/land-acquisition]
                [?lc :thk.lifecycle/activities ?act]
                [?project :thk.project/lifecycles ?lc]

                :in $ ?project ?seqno]
              db project-id sequence-number))))

(defn resolve-metadata
  "Given metadata parsed from a file name, resolve the coded references to
  database values."
  [db {project-thk-id :thk.project/id
       :keys [part activity task document-group original-name]
       :as metadata}]
  (let [project-eid [:thk.project/id project-thk-id]
        project-id (:db/id (du/entity db [:thk.project/id project-thk-id]))
        activity-id (when project-id
                      (ffirst
                       (d/q '[:find ?act
                              :where
                              [?project :thk.project/lifecycles ?lc]
                              [?lc :thk.lifecycle/activities ?act]
                              [(missing? $ ?lc :meta/deleted?)]
                              [?act :activity/name ?name]
                              [(missing? $ ?act :meta/deleted?)]
                              [?name :filename/code ?code]
                              :in $ ?project ?code]
                            db project-eid activity)))
        task-id (when activity-id
                  (ffirst
                   (d/q '[:find ?task
                          :where
                          [?act :activity/tasks ?task]
                          [(missing? $ ?task :meta/deleted?)]
                          [?task :task/type ?type]
                          [?type :filename/code ?code]
                          :in $ ?act ?code]
                        db activity-id task)))
        part (when (and task-id part)
               (ffirst
                (d/q '[:find (pull ?part [:db/id :file.part/name])
                       :where
                       [?part :file.part/task ?task]
                       [?part :file.part/number ?number]
                       :in $ ?task ?number]
                     db task-id (Long/parseLong part))))
        doc-group (when document-group
                    (ffirst (d/q '[:find ?ident
                                   :where
                                   [?dg :filename/code ?code]
                                   [?dg :db/ident ?ident]
                                   :in $ ?code]
                                 db document-group)))

        file-id (when (and task-id original-name)
                  ;; Find file that has same metadata, we need to pull
                  ;; each latest file in the task and fetch their metadata
                  ;; to compare to as we don't store the full metadata filename
                  ;; and the original filename could be just a description
                  ;; without metadata
                  (some (fn [[file-id]]
                          (when (= (filename-metadata/metadata->filename
                                    (file-metadata-by-id db file-id))
                                   original-name)
                            file-id))
                        (d/q '[:find ?f
                               :where
                               [?t :task/files ?f]
                               [(missing? $ ?f :meta/deleted?)]
                               [?f :file/upload-complete? true]
                               (not-join [?f]
                                         [?replacement :file/previous-version ?f])
                               :in $ ?t]
                             db task-id)))]
    (merge metadata
           {:project-eid project-eid
            :activity-id activity-id
            :task-id task-id
            :part-info part
            :document-group-kw doc-group
            :file-id file-id})))

(defn next-task-part-number
  "Return next available file part number for the given task.
  Part numbers start from 1 and end in 99. If no part number
  is available, returns nil.

  Numbers used by parts that have been removed are not considered
  available."
  [db task-id]
  (let [used-numbers
        (into #{}
              (map first)
              (d/q '[:find ?n
                     :where
                     [?e :file.part/task ?task]
                     [?e :file.part/number ?n]
                     :in $ ?task]
                   (d/history db) task-id))]
    (first (remove used-numbers (range 1 100)))))

(defn latest-version
  "Return the :db/id of the latest version of the given file."
  [db file-id]
  (loop [file (du/entity db file-id)]
    (if-let [newer-version (first (:file/_previous-version file))]
      (recur newer-version)
      (:db/id file))))

(defn previous-version
  "Return the id of the previous version of the given file."
  [db file-id]
  (->> file-id
       (du/entity db)
       :file/previous-version :db/id))

(defn file-versions
  "Returns all version of the given file.
  Chases down :file/previous-version links in both directions
  and returns the file ids in a vector (newest first, earliest last)."
  [db id]

  (vec
   (take-while some?
               (iterate (partial previous-version db)
                        (latest-version db id)))))


(defn search-files-in-project
  "Search for files in project that contain given text in their name.
  Returns list of file ids."
  [db project text]
  (mapv first
        (d/q '[:find ?f
               :in $ ?p ?text
               :where
               [?p :thk.project/lifecycles ?lc]
               [?lc :thk.lifecycle/activities ?a]
               [?a :activity/tasks ?t]
               [?t :task/files ?f]
               [?f :file/upload-complete? true]
               [(missing? $ ?f :meta/deleted?)]
               [?f :file/name ?file-name]
               [(teet.util.string/contains-words? ?file-name ?text)]]
             db project text)))

(defn search-latest-files-in-project
  "Search only for the latest files in project that contain given text in their name.
  Returns list of file ids."
  [db project text]
  (mapv first
    (d/q '[:find ?f
           :in $ ?p ?text
           :where
           [?p :thk.project/lifecycles ?lc]
           [?lc :thk.lifecycle/activities ?a]
           [?a :activity/tasks ?t]
           [?t :task/files ?f]
           [(missing? $ ?f :meta/deleted?)]
           [?f :file/upload-complete? true]
           [?f :file/name ?file-name]
           [(teet.util.string/contains-words? ?file-name ?text)]
           (not-join [?f]
             [?replacement :file/previous-version ?f])]
      db project text)))

(defn file-by-uuid
  "Return file :db/id based on the :file/id UUID."
  [db uuid]
  {:pre [(uuid? uuid)]}
  (ffirst
   (d/q '[:find ?f
          :where [?f :file/id ?uuid]
          :in $ ?uuid]
        db uuid)))

(defn file-navigation-info-by-uuid
  "Return file navigation info by file UUID.
  Returns file, task, activity, lifecycle and project
  ids the file is linked to."
  [db uuid]
  {:pre [(uuid? uuid)]}
  (first
   (d/q '[:find ?f ?t ?a ?l ?p
          :keys file task activity lifecycle project
          :where
          [?f :file/id ?uuid]
          [?t :task/files ?f]
          [?a :activity/tasks ?t]
          [?l :thk.lifecycle/activities ?a]
          [?p :thk.project/lifecycles ?l]
          :in $ ?uuid]
        db uuid)))

;; used by vektorio scheduled upload retry. considering only files that are older than threshold,
;; so we don't step on top of normally (by s3 trigger) started uploads.
(defn aged-task-files-without-model-id [db threshold-in-minutes]
  (let [modified-threshold (->> threshold-in-minutes
                                time/minutes
                                (time/minus (time/now))
                                tc/to-date)]
    (log/debug "modified-threshold" modified-threshold)
    (d/q '[:find ?f ?name ?mctime
           :keys file-eid file-name mctime
           :in $ ?modified-threshold
           :where
           [?f :file/id _]
           [?f :file/name ?name]
           [?f :meta/created-at ?created]
           [?f :file/size ?file-size]
           [(get-else $ ?f :meta/modified-at ?created) ?mctime]
           [(< ?mctime ?modified-threshold)]
           [?f :file/upload-complete? true]
           [(> ?file-size 0)]
           [(missing? $ ?f :vektorio/model-id)]
           [(missing? $ ?f :meta/deleted?)]]
         db modified-threshold)))

(defn task-has-files?
  "Check if task currently has files. Doesn't include deleted files."
  [db task-id]
  (boolean
    (d/qseq '[:find ?f
              :where
              [?t :task/files ?f]
              [?f :file/upload-complete? true]
              [(missing? $ ?f :meta/deleted?)]
              (not-join [?f]
                        [?replacement :file/previous-version ?f])
              :in $ ?t]
            db task-id)))

(defn activity-has-files?
  "Check if some task in activity has files. Doesn't include deleted tasks or files."
  [db activity-id]
  (boolean
   (d/qseq '[:find ?f
             :where
             [?act :activity/tasks ?task]
             [(missing? $ ?task :meta/deleted?)]
             [?task :task/files ?f]
             [?f :file/upload-complete? true]
             [(missing? $ ?f :meta/deleted?)]
             (not-join [?f]
                       [?replacement :file/previous-version ?f])
             :in $ ?act]
           db activity-id)))
