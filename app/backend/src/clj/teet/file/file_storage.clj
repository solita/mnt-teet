(ns teet.file.file-storage
  "Access to S3 bucket for storing files. Access must be checked in previous layers."
  (:require [teet.environment :as environment]
            [teet.integration.integration-s3 :as integration-s3]))

(def ^:const url-expiration-seconds 300)

(defn- storage-bucket []
  (environment/config-value :document-storage :bucket-name))

(defn download-url [content-disposition file-name]
  (integration-s3/presigned-url {:content-disposition content-disposition
                                 :expiration-seconds url-expiration-seconds}
                                "GET" (storage-bucket) file-name))

(defn upload-url [file-name]
  (integration-s3/presigned-url {:expiration-seconds url-expiration-seconds}
                                "PUT" (storage-bucket) file-name))

(defn document-s3-ref
  "Returns an integration S3 file descriptor for a document."
  [{id :db/id
    name :file/name}]
  {:bucket (storage-bucket)
   :file-key (str id "-" name)})
