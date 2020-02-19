(ns teet.document.document-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.document.document-spec]
            [teet.document.document-storage :as document-storage]
            [datomic.client.api :as d]
            [teet.project.project-db :as project-db]))

(defquery :document/download-file
  {:doc "Get a download link to the given file"
   :context {db :db}
   :args {file-id :file-id}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/view-document {:db/id file-id}}}
  (let [file-name (:file/name (d/pull db '[:file/name] file-id))
        s3-file-name (str file-id "-" file-name)]
    ^{:format :raw}
    {:status 302
     :headers {"Location" (document-storage/download-url s3-file-name)}}))
