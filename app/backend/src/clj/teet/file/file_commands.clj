(ns teet.file.file-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [datomic.client.api :as d]
            [teet.file.file-storage :as file-storage]
            teet.file.file-spec
            [teet.meta.meta-model :refer [creation-meta deletion-tx] :as meta-model]
            [teet.file.file-model :as file-model]
            [teet.project.project-db :as project-db]
            [teet.file.file-db :as file-db]
            [teet.log :as log]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]
            teet.file.file-tx
            [teet.util.collection :as cu]))

(defn- new-file-key [{name :file/name}]
  (str (java.util.UUID/randomUUID) "-" name))

(defn latest-file-version-id
  [db file-id]
  (if-let [parent (get-in (du/entity db file-id) [:file/_previous-version 0 :db/id])]
    (recur db parent)
    file-id))

(defn- find-previous-version
  ([db task-id previous-version]
   (find-previous-version db task-id previous-version []))
  ([db task-id previous-version optional-query-keys]
   (let [query-keys (into [:db/id :file/version :file/sequence-number
                           {:file/part [:db/id :file.part/number :file.part/name]}]
                          optional-query-keys)]
     (if-let [old-file (ffirst (d/q '[:find (pull ?f attrs)
                                      :in $ ?f ?t attrs
                                      :where
                                      [?t :task/files ?f]]
                                    db
                                    (latest-file-version-id db previous-version)
                                    task-id
                                    query-keys))]
       old-file
       (db-api/bad-request! "Can't find previous version")))))

(def file-keys [:file/name :file/size :file/document-group :file/sequence-number :file/part])

(defcommand :file/upload-attachment
  {:doc "Upload attachment file and optionally attach it to entity."
   :context {:keys [conn user db]}
   ;; TODO: Pass project id to check project authz
   :payload {:keys [file project-id attach-to]}
   :project-id project-id
   :authorization {:project/upload-comment-attachment {}}
   :pre [^{:error :comment-attachment-image-only}
         (or attach-to
             (file-model/image? file))

         ^{:error :attach-pre-check-failed}
         (or (nil? attach-to)
             (file-db/attach-to db user file attach-to))]}
  (log/debug "upload-attachment: got project-id" project-id)

  (let [{attach-to-eid :eid
         wrap-tx :wrap-tx} (when attach-to
                             (file-db/attach-to db user file attach-to))
        wrap-tx (or wrap-tx identity)
        key (new-file-key file)
        res (tx (wrap-tx
                 [(merge (select-keys file file-keys)
                         {:db/id "new-file"
                          :file/s3-key key}
                         (when attach-to-eid
                           {:file/attached-to attach-to-eid})
                         (creation-meta user))]))
        file-id (get-in res [:tempids "new-file"])]

    {:url (file-storage/upload-url key)
     :file (d/pull (:db-after res) '[*] file-id)}))


(defcommand :file/delete-attachment
  {:doc "Delete an attachment"
   :context {:keys [user db]}
   :payload {:keys [file-id attached-to]}
   :project-id nil
   :authorization {}
   :pre [(or attached-to
             (file-db/own-file? db user file-id))]
   :transact (if attached-to
               (file-db/delete-attachment db user file-id attached-to)
               [(deletion-tx user file-id)])})


(defcommand :file/upload-complete
  {:doc "Mark file upload as complete"
   :context {:keys [conn user db]}
   :payload {id :db/id}
   :pre [(file-db/own-file? db user id)]
   ;; No need to check extra authorization again, as we check pre condition that this
   ;; is the user's own uploaded file
   :project-id nil
   :authorization {}
   :transact [{:db/id id
               :file/upload-complete? true}]})

(defn file-belong-to-task?
  [db task-id file-id]
  (let [file-entity (du/entity db file-id)]
    (= task-id (get-in file-entity [:task/_files 0 :db/id]))))

(defcommand :file/replace
  {:doc "replace existing file with a new version. Save file size"
   :context {:keys [conn user db]}
   :payload {:keys [task-id file previous-version-id]}
   :project-id (project-db/task-project-id db task-id)
   :pre [^{:error :invalid-previous-file}
         (file-belong-to-task? db task-id previous-version-id)
         ^{:error :replaced-file-not-latest}
         (= (latest-file-version-id db previous-version-id) previous-version-id)]
   :authorization {:document/upload-document {:db/id task-id
                                              :link :task/assignee}}}
  (or (file-model/validate-file file)
      (let [old-file (find-previous-version db task-id previous-version-id [:file/name :file/document-group])
            version (-> old-file
                        :file/version
                        inc)
            key (new-file-key file)
            tx-data [(list 'teet.file.file-tx/upload-file-to-task
                           {:db/id task-id
                            :task/files [(cu/without-nils
                                           (merge
                                             old-file
                                             (select-keys file [:file/size])
                                             {:db/id "new-file"
                                              :file/s3-key key
                                              :file/original-name (:file/name file)
                                              :file/version version
                                              :file/previous-version (:db/id old-file)
                                              :file/status :file.status/draft}
                                             (creation-meta user)))]})]
            res (tx tx-data)
            file-id (get-in res [:tempids "new-file"])]
        (try
          {:url (file-storage/upload-url key)
           :task-id task-id
           :file (d/pull (:db-after res) '[*] file-id)}
          (catch Exception e
            (log/warn e "Unable to create S3 presigned URL")
            (throw e))))))

(defcommand :file/upload
  {:doc "Upload new file to task."
   :context {:keys [conn user db]}
   :payload {:keys [task-id attachment? file previous-version-id] :as p}
   :project-id (project-db/task-project-id db task-id)
   :pre [^{:error :invalid-previous-file}
         (or (nil? previous-version-id)
             (file-belong-to-task? db task-id previous-version-id))]
   :authorization {:document/upload-document {:db/id task-id
                                              :link :task/assignee}}}
  (or (file-model/validate-file file)
      (let [old-file (when previous-version-id
                       (find-previous-version db task-id previous-version-id))
            version (or (some-> old-file :file/version inc) 1)

            key (new-file-key file)
            tx-data [(list 'teet.file.file-tx/upload-file-to-task
                           {:db/id (or task-id "new-task")
                            :task/files [(cu/without-nils
                                          (merge (select-keys file file-keys)
                                                 {:db/id "new-file"
                                                  :file/s3-key key
                                                  :file/status :file.status/draft
                                                  :file/version version
                                                  :file/original-name (:file/name file)}
                                                 (when (:file/description file)
                                                   {:file/name (str (:file/description file) "." (:file/extension file))})
                                                 (when old-file
                                                   {:file/previous-version (:db/id old-file)})

                                                 ;; Replacement version is uploaded to the same part
                                                 (when-let [old-part (:file/part old-file)]
                                                   {:file/part old-part})
                                                 (when-let [old-seq-number (:file/sequence-number old-file)]
                                                   {:file/sequence-number old-seq-number})
                                                 (creation-meta user)))]})]
            res (tx tx-data)
            t-id (or task-id (get-in res [:tempids "new-task"]))
            file-id (get-in res [:tempids "new-file"])]
        (try
          {:url (file-storage/upload-url key)
           :task-id t-id
           :file (d/pull (:db-after res) '[*] file-id)}
          (catch Exception e
            (log/warn e "Unable to create S3 presigned URL")
            (throw e))))))

(defcommand :file/delete
  {:doc "Delete file and all its versions."
   :context {:keys [user db]}
   :payload {:keys [file-id status]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/delete-document {:db/id file-id}}
   :transact (vec
              (for [version-id (file-db/file-versions db file-id)]
                (deletion-tx user version-id)))})

(defcommand :file/seen
  {:doc "Mark that I have seen this file"
   :context {:keys [user db]}
   :payload {:keys [file-id]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/view-document {:db/id file-id}}
   :transact [(let [user-id (->> user user-model/user-ref (du/entity db) :db/id)]
                {:db/id "file-seen"
                 :file-seen/file file-id
                 :file-seen/user user-id
                 :file-seen/file+user [file-id user-id]
                 :file-seen/seen-at (java.util.Date.)})]})

(defcommand :file/modify
  {:doc "Modify task file info: part, group, sequence and name."
   :context {:keys [user db]}
   :payload {id :db/id :as file}
   :project-id (project-db/file-project-id db id)
   :authorization {:document/overwrite-document {:db/id id}}
   :transact [(list 'teet.file.file-tx/modify-file
                    (merge file
                           (meta-model/modification-meta user)))]})
