(ns teet.file.file-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [datomic.client.api :as d]
            [teet.file.file-storage :as file-storage]
            teet.file.file-spec
            [teet.meta.meta-model :refer [modification-meta creation-meta deletion-tx]]
            [teet.file.file-model :as file-model]
            [teet.project.project-db :as project-db]))



(defn- find-previous-version [db task-id previous-version]
  (if-let [old-file (ffirst (d/q '[:find (pull ?f [:db/id :file/version])
                                   :in $ ?f ?t
                                   :where
                                   [?t :task/files ?f]]
                                 db previous-version task-id))]
    old-file
    (db-api/bad-request! "Can't find previous version")))

(defcommand :file/upload
  {:doc "Upload new file to task. If previous-version is specified, this file is a new version of a previous file."
   :context {:keys [conn user db]}
   :payload {:keys [task-id file previous-version-id]}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:document/upload-document {:db/id task-id}}}
  (let [file (file-model/type-by-suffix file)]
    (or (file-model/validate-file file)
        (let [old-file (when previous-version-id
                         (find-previous-version db task-id previous-version-id))
              version (or (some-> old-file :file/version inc) 1)
              res (tx [{:db/id (or task-id "new-task")
                        :task/files [(merge file
                                            {:db/id "new-file"
                                             :file/version version}
                                            (when old-file
                                              {:file/previous-version (:db/id old-file)})
                                            (creation-meta user))]}])
              t-id (or task-id (get-in res [:tempids "new-task"]))
              file-id (get-in res [:tempids "new-file"])
              key (str file-id "-" (:file/name file))]

          {:url (file-storage/upload-url key)
           :task-id t-id
           :file (d/pull (:db-after res) '[*] file-id)}))))

(defcommand :file/update-status
  {:doc "Update status of a file"
   :context {:keys [user db]}
   :payload {:keys [file-id status]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/update-document-status {:db/id file-id}}
   :transact [(merge {:db/id file-id
                      :file/status status}
                     (modification-meta user))]})

(defcommand :file/delete
  {:doc "Delete file"
   :context {:keys [user db]}
   :payload {:keys [file-id status]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/delete-document {:db/id file-id}}
   :transact [(deletion-tx user file-id)]})


#_(defmethod db-api/command! :document/delete-file [{conn :conn
                                                   user :user}
                                                  {:keys [file-id]}]
  (d/transact
   conn
    {:tx-data [(deletion-tx user file-id)]})
  :ok)
