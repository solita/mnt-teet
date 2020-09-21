(ns teet.file.file-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [datomic.client.api :as d]
            [teet.file.file-storage :as file-storage]
            teet.file.file-spec
            [teet.meta.meta-model :refer [creation-meta deletion-tx]]
            [teet.file.file-model :as file-model]
            [teet.project.project-db :as project-db]
            [clojure.string :as str]
            [teet.file.file-db :as file-db]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.log :as log]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]))

(defn- new-file-key [{name :file/name}]
  (str (java.util.UUID/randomUUID) "-" name))

(defn latest-file-version-id
  [db file-id]
  (if-let [parent (get-in (du/entity db file-id) [:file/_previous-version 0 :db/id])]
    (recur db parent)
    file-id))

(defn- find-previous-version [db task-id previous-version]
  (if-let [old-file (ffirst (d/q '[:find (pull ?f [:db/id :file/version :file/pos-number])
                                   :in $ ?f ?t
                                   :where
                                   [?t :task/files ?f]]
                                 db (latest-file-version-id db previous-version) task-id))]
    old-file
    (db-api/bad-request! "Can't find previous version")))

(def file-keys [:file/name :file/size :file/group-number :file/pos-number])

(defn check-image-only [file]
  (when-not (file-model/image? file)
    (db-api/bad-request! "Not allowed as attachment")))

(defcommand :file/upload-attachment
  {:doc "Upload attachment file and optionally attach it to entity."
   :context {:keys [conn user db]}
   ;; TODO: Pass project id to check project authz
   :payload {:keys [file project-id attach-to]}
   :project-id project-id
   :authorization {:project/upload-comment-attachment {}}
   :pre [^{:error :comment-attachment-image-only}
         (or attach-to (check-image-only file))

         ^{:error :attach-pre-check-failed}
         (or (nil? attach-to)
             (file-db/attach-to db user file attach-to))]}
  (log/debug "upload-attachment: got project-id" project-id)

  (let [key (new-file-key file)
        res (tx [(merge (select-keys file file-keys)
                        {:db/id "new-file"
                         :file/s3-key key}
                        (when attach-to
                          {:file/attached-to (file-db/attach-to db user file
                                                                attach-to)})
                        (creation-meta user))])
        file-id (get-in res [:tempids "new-file"])]

    {:url (file-storage/upload-url key)
     :file (d/pull (:db-after res) '[*] file-id)}))


(defcommand :file/delete-attachment
  {:doc "Delete an attachment"
   :context {:keys [user db]}
   :payload {:keys [file-id attached-to]}
   :project-id nil
   :authorization {}
   :pre [(or (and attached-to
                  (file-db/allow-delete-attachment? db user
                                                    file-id
                                                    attached-to)
                  (file-db/file-is-attached-to? db file-id attached-to))
             (file-db/own-file? db user file-id))]
   :transact [(deletion-tx user file-id)]})

(defn- file-with-metadata [{:file/keys [name] :as file}]
  (let [metadata (try
                   (filename-metadata/filename->metadata name)
                   (catch Exception e
                     (log/warn e "Uploading file with invalid metadata!")
                     nil))]
    (if metadata
      (merge file
             {:file/name (:name metadata)
              :file/group-number (:group metadata)})
      (update file :file/name
              (fn [n]
                ;; Take part after last underscore to skip over invalid metadata
                (last (str/split n #"_")))))))

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

(defcommand :file/upload
  {:doc "Upload new file to task."
   :context {:keys [conn user db]}
   :payload {:keys [task-id attachment? file previous-version-id]}
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
            file (file-with-metadata file)
            key (new-file-key file)
            res (tx [{:db/id (or task-id "new-task")
                      :task/files [(merge (select-keys file file-keys)
                                          {:db/id "new-file"
                                           :file/s3-key key
                                           :file/status :file.status/draft
                                           :file/version version}
                                          (when old-file
                                            {:file/previous-version (:db/id old-file)})
                                          (when-let [old-pos-number (:file/pos-number old-file)]
                                            {:file/pos-number old-pos-number})
                                          (creation-meta user))]}])
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
  {:doc "Delete file"
   :context {:keys [user db]}
   :payload {:keys [file-id status]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/delete-document {:db/id file-id}}
   :transact [(deletion-tx user file-id)]})

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
