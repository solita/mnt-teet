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
            [teet.log :as log]
            [clojure.string :as str])
  (:import (java.net URLEncoder)
           (net.coobird.thumbnailator Thumbnailator)
           (java.util UUID)))


(defn- url-for-file [db file-id with-metadata?]
  (let [s3-file-name (:file/s3-key (du/entity db file-id))]
    ^{:format :raw}
    {:status 302
     :headers {"Location" (file-storage/download-url
                            (when with-metadata?
                              ;; Get file metadata for downloads
                              (str "attachment; filename="
                                   (str/replace (->> file-id
                                                     (file-db/file-metadata-by-id db)
                                                     filename-metadata/metadata->filename
                                                     URLEncoder/encode)
                                                "+" "%20")))
                            s3-file-name)}}))

(defquery :file/download-file
  {:doc "Get a download link to the given file"
   :context {db :db}
   :args {file-id :file-id}
   :project-id (project-db/file-project-id db file-id)}
  (url-for-file db file-id true))

(defquery :file/thumbnail
  {:doc "Download small image preview of the given file (must be an image)"
   :context {db :db}
   :args {file-id :file-id
          thumbnail-size :size}
   :project-id (project-db/file-project-id db file-id)
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
   :pre [(or
           (and comment-id
               (file-db/file-is-attached-to-comment? db file-id comment-id)
               (authorization-check/authorized? user :document/view-document
                 (merge (url-for-file db file-id false)
                   {:project-id (when comment-id (project-db/comment-project-id db comment-id))})))
           (and attached-to
             (file-db/allow-download-attachments? db user attached-to)
             (file-db/file-is-attached-to? db file-id attached-to))
           (and attached-to
                (file-db/is-key-user-file? db file-id (second attached-to)))
           (file-db/own-file? db user file-id))]
   :allowed-for-all-users? true}
  (url-for-file db file-id false))

(defquery :file/resolve-metadata
  {:doc "Resolve file metadata"
   :context {:keys [db]}
   :args {name :file/name}
   :allowed-for-all-users? true}
  (try
    (->> name
         filename-metadata/filename->metadata
         (merge {:original-name name})
         (file-db/resolve-metadata db))
    (catch Exception _e
      ;; If metadata can't be parsed, return empty map, frontend will
      ;; know that filename is not valid
      {})))

(defn- valid-export-zip-filename?
  [filename]
  (let [{extension :extension
         description :description} (filename-metadata/name->description-and-extension filename)]
    (and (file-model/valid-chars-in-description? description)
         (= extension "zip"))))

(defquery :file/redirect-to-zip
  {:doc "URL endpoint for redirecting to AWS download of a generated zip file"
   :context {:keys [db]}
   :args {s3-key :s3-key
          filename :filename}
   :config {export-bucket [:document-storage :export-bucket-name]}
   :unauthenticated? true
   :pre [^{:error :configuration-missing}
         (some? export-bucket)
         ^{:error :invalid-filename}
         (valid-export-zip-filename? filename)
         (UUID/fromString s3-key)]}
  ^{:format :raw}
  {:status 302
   :headers {"Location" (integration-s3/presigned-url
                          {:content-disposition (str "attachment; filename=" filename)
                           :expiration-seconds 60}
                          "GET" export-bucket s3-key)}})
