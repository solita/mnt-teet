(ns teet.document.document-queries
  (:require [teet.db-api.core :as db-api]
            [teet.document.document-specs]
            [teet.document.document-storage :as document-storage]
            [datomic.client.api :as d]))


(defmethod db-api/query :document/download-file [{db :db} {file-id :file-id}]
  (let [file-name (:file/name (d/pull db '[:file/name] file-id))
        s3-file-name (str file-id "-" file-name)]
    ^{:format :raw}
    {:status 302
     :headers {"Location" (document-storage/download-url s3-file-name)}}))

(defmethod db-api/query :document/list-project-documents [{db :db} {:keys [thk-project-id]}]
  {:query '[:find (pull ?e [:db/id :document/name :document/size :document/type])
            :in $ ?project-id
            :where [?e :thk/id ?project-id] [?e :document/name _]]
   :args [db thk-project-id]
   :result-fn (partial mapv first)})

(defmethod db-api/query :document/fetch-document [{db :db} {document-id :document-id}]
  (let [sort-children (fn [doc path by-key]
                        (update doc path (fn [items] (sort-by by-key items))))
        doc (-> (d/q '[:find (pull ?e [:document/name
                                       :document/description
                                       :document/status
                                       :document/modified
                                       :db/id
                                       {:task/_documents [:db/id :task/description
                                                          {:task/type [:db/ident]}]}
                                       {:document/comments [:db/id :comment/comment :comment/timestamp
                                                            {:comment/author [:user/id]}]}
                                       {:document/files [:db/id :file/name :file/type :file/size
                                                         :file/author :file/timestamp]}])
                       :in $ ?e]
                     db document-id)
                ffirst
                (sort-children :document/comments :comment/timestamp)
                (sort-children :document/files :file/timestamp))]
    doc))
