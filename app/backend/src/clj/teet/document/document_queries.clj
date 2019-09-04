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
