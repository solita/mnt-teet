(ns teet.document.document-queries
  (:require [teet.db-api.core :as db-api]
            [teet.document.document-specs]
            [teet.document.document-storage :as document-storage]
            [datomic.client.api :as d]
            [taoensso.timbre :as log]))


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
  (let [docs (-> (d/q '[:find (pull ?e [:document/name :document/description :document/status
                                        {:document/comments [:comment/comment :comment/timestamp
                                                             {:comment/author [:user/id]}]}])
                        :in $ ?e]
                      db document-id)
                 ffirst
                 (update :document/comments (fn [comments]
                                              (sort-by :comment/timestamp comments))))
        files (->> (d/q '{:find [(pull ?e [*])
                                 (pull ?tx [:db/txInstant :tx/author])]
                          :in [$ ?doc]
                          :where [[?doc :document/files ?e ?tx]]}
                        db document-id)
                   (mapv (fn [[file tx]]
                           (merge file tx)))
                   (sort-by :db/txInstant))]
    (assoc docs :document/files files)))
