(ns teet.file.file-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.file.file-spec]
            [teet.file.file-storage :as file-storage]
            [teet.project.project-db :as project-db]
            [teet.file.file-db :as file-db]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.util.datomic :as du]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.file.file-model :as file-model]
            [datomic.client.api :as d]
            [ring.util.io :as ring-io]
            [teet.integration.integration-s3 :as integration-s3]
            [teet.log :as log])
  (:import (java.net URLEncoder)
           (net.coobird.thumbnailator Thumbnailator)))


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

(defquery :file/latest-version
  {:doc "Get the id of the latest version of a file"
   :context {db :db}
   :args {file-id :file-id}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/view-document {:db/id file-id}}}
  (file-db/latest-version db file-id))

(defquery :file/thumbnail
  {:doc "Download small image preview of the given file (must be an image)"
   :context {db :db}
   :args {file-id :file-id
          thumbnail-size :size}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/view-document {:db/id file-id}}
   :pre [(file-model/image? (d/pull db [:file/name] file-id))
         (<= 32 thumbnail-size 256)]}
  (let [bucket (file-storage/storage-bucket)
        {:file/keys [s3-key size]} (d/pull db [:file/s3-key :file/size] file-id)
        thumbnail-key (str s3-key "-thumbnail-" thumbnail-size ".png")]

    (if (< size file-model/image-thumbnail-size-threshold)
      ;; This file is too small to bother with thumbnails, let user download it as is
      (url-for-file db file-id false)

      (do
        ;; If thumbnail does not exist yet, generate and upload it
        (when-not (integration-s3/exists? bucket thumbnail-key)
          (log/info "Generating thumbnail:" thumbnail-key)
          (integration-s3/put-object
           bucket thumbnail-key
           (ring-io/piped-input-stream
            (fn [out]
              (try
                (Thumbnailator/createThumbnail
                 (integration-s3/get-object (file-storage/storage-bucket)
                                            s3-key)
                 out "png" thumbnail-size thumbnail-size)
                (catch Exception e
                  (log/error e "Error generating thumbnail")))))))

        ;; then return a presigned URL for it
        ^{:format :raw}
        {:status 302
         :headers {"Location" (file-storage/download-url "inline"
                                                         thumbnail-key)}}))))



(defquery :file/download-attachment
  {:doc "Download comment attachment"
   :context {:keys [db user]}
   :args {:keys [file-id attached-to comment-id]}
   :pre [(or (and comment-id
                  (file-db/file-is-attached-to-comment? db file-id comment-id)
                  (authorization-check/authorized? user :document/view-document
                                                   (url-for-file db file-id false)))
             (and attached-to
                  (file-db/allow-download-attachments? db user attached-to)
                  (file-db/file-is-attached-to? db file-id attached-to))

             (file-db/own-file? db user file-id))]
   :project-id nil
   :authorization {}}
  (url-for-file db file-id false))

(defquery :file/resolve-metadata
  {:doc "Resolve file metadata"
   :context {:keys [db]}
   :args {name :file/name}
   :project-id nil
   :authorization {}}
  (try
    (->> name
         filename-metadata/filename->metadata
         (merge {:original-name name})
         (file-db/resolve-metadata db))
    (catch Exception _e
      ;; If metadata can't be parsed, return empty map, frontend will
      ;; know that filename is not valid
      {})))
