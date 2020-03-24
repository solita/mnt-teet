(ns teet.file.file-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.file.file-storage :as file-storage]
            teet.file.file-spec
            [teet.meta.meta-model :refer [modification-meta creation-meta deletion-tx]]
            [teet.util.collection :as cu]
            [teet.file.file-model :as file-model]))


;; Create new document and link it to task. Returns entity id for document.
#_(defmethod db-api/command! :file/new-document [{conn :conn
                                                    user :user} {:keys [task-id document]}]
  (let [author-id (get-in document [:document/author :user/id])]
    (-> conn
        (d/transact {:tx-data [(merge {:db/id "new-document"}
                                      (cu/without-nils (select-keys document
                                                                    [:document/name :document/status :document/description :document/category :document/sub-category]))
                                      (when author-id
                                        {:document/author [:user/id author-id]})
                                      (creation-meta user))
                               {:db/id          task-id
                                :task/documents [{:db/id "new-document"}]}]})
        (get-in [:tempids "new-document"]))))

#_(defmethod db-api/command! :document/edit-document [{conn :conn
                                                     user :user}
                                                    {:keys [document]}]
  (let [author-id (get-in document [:document/author :user/id])]
    (d/transact conn {:tx-data
                      [(merge (cu/without-nils (select-keys document
                                                            [:db/id :document/name :document/status :document/description :document/category :document/sub-category]))
                              (when author-id
                                {:document/author [:user/id author-id]})
                              (modification-meta user))]}))
  :ok)

(defmethod db-api/command! :task/upload-file [{conn :conn
                                               user :user}
                                              {:keys [task-id file]}]
  (let [file (file-model/type-by-suffix file)]
    (or (file-model/validate-file file)
        (let [res (d/transact conn {:tx-data [{:db/id (or task-id "new-task")
                                               :task/files (merge file
                                                                      {:db/id "new-file"}
                                                                      (creation-meta user))}
                                              {:db/id "datomic.tx"
                                               :tx/author (:user/id user)}]})
              t-id (or task-id (get-in res [:tempids "new-task"]))
              file-id (get-in res [:tempids "new-file"])
              key (str file-id "-" (:file/name file))]

          {:url (file-storage/upload-url key)
           :task-id t-id
           :file (d/pull (:db-after res) '[*] file-id)}))))


#_(defmethod db-api/command! :document/delete [{conn :conn
                                              user :user}
                                             {:keys [document-id]}]
  (d/transact
    conn
    {:tx-data [(deletion-tx user document-id)]})
  :ok)

#_(defmethod db-api/command! :document/delete-file [{conn :conn
                                                   user :user}
                                                  {:keys [file-id]}]
  (d/transact
   conn
    {:tx-data [(deletion-tx user file-id)]})
  :ok)
