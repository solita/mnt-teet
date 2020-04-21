(ns teet.file.file-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [datomic.client.api :as d]
            [teet.file.file-storage :as file-storage]
            teet.file.file-spec
            [teet.meta.meta-model :refer [modification-meta creation-meta deletion-tx]]
            [teet.file.file-model :as file-model]
            [teet.project.project-db :as project-db]
            [clojure.string :as str]
            [teet.file.file-db :as file-db]))



(defn- find-previous-version [db task-id previous-version]
  (if-let [old-file (ffirst (d/q '[:find (pull ?f [:db/id :file/version])
                                   :in $ ?f ?t
                                   :where
                                   [?t :task/files ?f]]
                                 db previous-version task-id))]
    old-file
    (db-api/bad-request! "Can't find previous version")))

(def file-keys [:file/name :file/size :file/type])

(defn check-image-only [file]
  (when-not (str/starts-with? (:file/type file) "image/")
    (db-api/bad-request! "Not allowed as attachment")))

(defcommand :file/upload-attachment
  {:doc "Upload attachment file that is not linked to anything yet"
   :context {:keys [conn user db]}
   ;; TODO: Pass project id to check project authz
   :payload {:keys [file project-id]}
   :project-id project-id
   :authorization {:project/upload-comment-attachment {}}}
  (let [file (file-model/type-by-suffix file)]
    (check-image-only file)
    (let [res (tx [(merge (select-keys file file-keys)
                          {:db/id "new-file"}
                          (creation-meta user))])
          file-id (get-in res [:tempids "new-file"])
          key (str file-id "-" (:file/name file))]

      {:url (file-storage/upload-url key)
       :file (d/pull (:db-after res) '[*] file-id)})))


(defcommand :file/delete-attachment
  {:doc "Delete an attachment"
   :context {:keys [user db]}
   :payload {:keys [file-id]}
   :project-id nil
   :authorization {}
   :pre [(file-db/own-file? db user file-id)]
   :transact [(deletion-tx user file-id)]})

(defcommand :file/upload
  {:doc "Upload new file to task."
   :context {:keys [conn user db]}
   :payload {:keys [task-id attachment? file previous-version-id]}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:document/upload-document {:db/id task-id
                                              :link :task/assignee}}}
  (let [file (file-model/type-by-suffix file)]
    (or (file-model/validate-file file)
        (let [old-file (when previous-version-id
                         (find-previous-version db task-id previous-version-id))
              version (or (some-> old-file :file/version inc) 1)
              res (tx [{:db/id (or task-id "new-task")
                        :task/files [(merge (select-keys file file-keys)
                                            {:db/id "new-file"
                                             :file/status :file.status/draft
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

(defcommand :file/delete
  {:doc "Delete file"
   :context {:keys [user db]}
   :payload {:keys [file-id status]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/delete-document {:db/id file-id}}
   :transact [(deletion-tx user file-id)]})
