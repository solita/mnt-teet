(ns teet.file.file-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.file.file-spec]
            [teet.file.file-storage :as file-storage]
            [teet.project.project-db :as project-db]
            [teet.file.file-db :as file-db]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.util.datomic :as du])
  (:import (java.net URLEncoder)))


(defn- url-for-file [db file-id with-metadata?]
  (let [s3-file-name (:file/s3-key (du/entity db file-id))]
    ^{:format :raw}
    {:status 302
     :headers {"Location" (file-storage/download-url
                           (when with-metadata?
                             ;; Get file metadata for downloads
                             (str "attachment; filename="
                                  (->> file-id
                                       (file-db/file-metadata db)
                                       filename-metadata/metadata->filename
                                       URLEncoder/encode)))
                           s3-file-name)}}))

(defquery :file/download-file
  {:doc "Get a download link to the given file"
   :context {db :db}
   :args {file-id :file-id}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/view-document {:db/id file-id}}}
  (url-for-file db file-id true))



(defquery :file/download-attachment
  {:doc "Download comment attachment"
   :context {:keys [db user]}
   :args {:keys [file-id attached-to comment-id]}
   :pre [(or (and comment-id
                  (file-db/file-is-attached-to-comment? db file-id comment-id))
             (and attached-to
                  (file-db/allow-download-attachments? db user attached-to)
                  (file-db/file-is-attached-to? db file-id attached-to))

             (file-db/own-file? db user file-id))]
   :project-id (when comment-id
                 (project-db/comment-project-id db comment-id))
   :authorization {:document/view-document {:db/id comment-id}}}
  (url-for-file db file-id false))
