(ns teet.file.file-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.file.file-spec]
            [teet.file.file-storage :as file-storage]
            [datomic.client.api :as d]
            [teet.project.project-db :as project-db]))

(defquery :file/download-file
  {:doc "Get a download link to the given file"
   :context {db :db}
   :args {file-id :file-id}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/view-document {:db/id file-id}}}
  (let [file-name (:file/name (d/pull db '[:file/name] file-id))
        s3-file-name (str file-id "-" file-name)]
    ^{:format :raw}
    {:status 302
     :headers {"Location" (file-storage/download-url s3-file-name)}}))
