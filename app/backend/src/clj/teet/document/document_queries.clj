(ns teet.document.document-queries
  (:require [teet.db-api.core :as db-api]
            [teet.document.document-specs]
            [teet.document.document-storage :as document-storage]
            [datomic.client.api :as d]))


(defmethod db-api/query :document/download [{db :db} {doc-id :db/id}]
  (let [{doc-name :document/name}
        (d/pull db '[:document/name] doc-id)
        file-name (str doc-id "-" doc-name)]
    ^{:format :raw}
    {:status 302
     :headers {"Location" (document-storage/download-url file-name)}}))

(defmethod db-api/query :document/list-project-documents [{db :db} {:keys [thk-project-id]}]
  {:query '[:find (pull ?e [:db/id :document/name :document/size :document/type])
            :in $ ?project-id
            :where [?e :thk/id ?project-id] [?e :document/name _]]
   :args [db thk-project-id]
   :result-fn (partial mapv first)})

(defmethod db-api/query :document/fetch-document [{db :db} {document-id :document-id}]
  {:query '[:find (pull ?e [:document/description :document/status
                            {:document/files [:file/name :file/size :file/type]}
                            {:document/comments [:comment/comment :comment/timestamp
                                                 {:comment/author [:user/id]}]}])
            :in $ ?e]
   :args [db document-id]
   :result-fn #(-> %
                   ffirst
                   (update :document/comments (fn [comments] (sort-by :document/timestamp comments))))})
