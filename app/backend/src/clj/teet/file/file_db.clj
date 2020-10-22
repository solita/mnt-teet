(ns teet.file.file-db
  "File queries"
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.comment.comment-db :as comment-db]
            [teet.log :as log]
            [teet.file.filename-metadata :as filename-metadata]))

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

  Default behaviour is to disallow and throw an exception."
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

(defn file-listing
  "Fetch file information suitable for file listing. Returns all file attributes
  and owner info. Returns only the latest version of each file with previous versions
  as :versions key.

  Includes the timestamp user has seen the file (if any) as :file-seen/seen-at."
  [db user file-rows]
  ;; TODO separate comment-count per file and file listing
  (log/debug "firs row" (first file-rows))
  (let [;; File seen statuses
        file-ids (mapv first file-rows)
        seen-at-by-file (files-seen-at db user file-ids)

        ;; Get comment counts for files
        comment-counts-by-file
        (file-ids-with-comment-counts db file-ids user)

        file-eid->task-eids (into {}
                                  (map (fn [[file-eid activity-eid task-eid]]
                                         [file-eid [activity-eid task-eid]])
                                       file-rows))
        
        files
        (mapv
         (comp #(merge %
                       (comment-counts-by-file (:db/id %))
                       {:file-seen/seen-at (seen-at-by-file (:db/id %))})
               first)
         (d/q '[:find (pull ?f [:db/id :file/name :meta/deleted? :file/version :file/size :file/status :file/part
                                {:file/previous-version [:db/id]}
                                :meta/created-at
                                {:meta/creator [:user/id :user/family-name :user/given-name]}])
                :where
                [?f :file/upload-complete? true]
                :in $ [?f ...]] db file-ids))        
        file-with-task-eids (fn [file]
                              (let [file-eid (:db/id file)
                                    [activity-eid task-eid] (get file-eid->task-eids file-eid)]
                                (assoc file
                                       :activity-eid activity-eid
                                       :task-eid task-eid)))
        files (map file-with-task-eids files)
        ;; Group files to first versions and next versions
        {next-versions true
         first-versions false}
        (group-by (comp boolean :file/previous-version) files)

        ;; Find next version for given file
        next-version (fn [file]
                       (some #(when (= (:db/id file)
                                       (get-in % [:file/previous-version :db/id])) %)
                             next-versions))]
    (vec
     (for [f first-versions
           :let [versions (filter (complement :meta/deleted?)
                                  (reverse
                                   (take-while some? (iterate next-version f))))
                 [latest-version & previous-versions] versions]
           :when latest-version]
       (assoc latest-version
              :versions previous-versions)))))

(defn land-files-by-project-and-sequence-number [db user project-id sequence-number]
  (let [query-result (d/q '[:find ?f ?act ?task
                            :where
                            ;; File has this sequence number
                            [?f :file/sequence-number ?seqno]
                            
                            ;; File belongs to the project & is in land-acquisition
                            [?task :task/files ?f]
                            [?act :activity/tasks ?task]
                            [?act :activity/name :activity.name/land-acquisition]
                            [?lc :thk.lifecycle/activities ?act]
                            [?project :thk.project/lifecycles ?lc]
                            
                            :in $ ?project ?seqno]
                          db project-id sequence-number)]
    (log/debug "first of query-result" (first query-result))
    (file-listing
     db user query-result)))

(defn resolve-metadata
  "Given metadata parsed from a file name, resolve the coded references to
  database values."
  [db {project-thk-id :thk.project/id
       :keys [part activity task document-group original-name]
       :as metadata}]
  (let [project-eid [:thk.project/id project-thk-id]
        activity-id (ffirst
                     (d/q '[:find ?act
                            :where
                            [?project :thk.project/lifecycles ?lc]
                            [?lc :thk.lifecycle/activities ?act]
                            [?act :activity/name ?name]
                            [?name :filename/code ?code]
                            :in $ ?project ?code]
                          db project-eid activity))
        task-id (when activity-id
                  (ffirst
                   (d/q '[:find ?task
                          :where
                          [?act :activity/tasks ?task]
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
                  (ffirst
                   (d/q '[:find ?f
                          :where
                          [?f :file/original-name ?n]
                          [?t :task/files ?f]
                          :in $ ?t ?n]
                        db task-id original-name)))]
    (merge metadata
           {:project-eid project-eid
            :activity-id activity-id
            :task-id task-id
            :part-info part
            :document-group-kw doc-group
            :file-id file-id})))

(defn file-metadata
  "Return file metadata for a given file id."
  [db file-id]
  (let [{:file/keys [name sequence-number] :as file}
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
                    {:file/part [:file.part/number]}] file-id)]
    (merge
     (filename-metadata/name->description-and-extension name)
     (when-let [p (get-in file [:task/_files 0 :activity/_tasks 0 :thk.lifecycle/_activities 0 :thk.project/_lifecycles 0 :thk.project/id])]
       {:thk.project/id p})
     (when sequence-number
       {:sequence-number sequence-number})
     (if-let [dg (get-in file [:file/document-group :filename/code])]
       {:document-group dg})
     (when-let [act (get-in file [:task/_files 0 :activity/_tasks 0 :activity/name :filename/code])]
       {:activity act})
     (when-let [task (get-in file [:task/_files 0 :task/type :filename/code])]
       {:task task})
     {:part (or (some->> file :file/part :file.part/number
                         (format "%02d"))
                "00")})))

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
    (first (remove used-numbers (drop 1 (range 1 100))))))
