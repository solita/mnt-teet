(ns teet.file.file-storage
  "Access to S3 bucket for storing files. Access must be checked in previous layers."
  (:require [amazonica.aws.s3 :as s3]
            [teet.environment :as environment]
            [teet.integration.integration-s3 :as integration-s3])
  (:import (java.util Date)
           (com.amazonaws.services.s3.model GeneratePresignedUrlRequest
                                            ResponseHeaderOverrides)
           (com.amazonaws HttpMethod)))

(def ^:const url-expiration-ms 300000)

(defn- expiration-date []
  (Date. (+ (System/currentTimeMillis) url-expiration-ms)))

(defn- storage-bucket []
  (environment/config-value :document-storage :bucket-name))

(comment
  (defn- presigned-url [method content-disposition file-name]
    (let [request (-> (GeneratePresignedUrlRequest. (storage-bucket)
                                                    file-name
                                                    (HttpMethod/valueOf method))
                      (.withExpiration (expiration-date)))
          request (if content-disposition
                    (.withResponseHeaders
                     request
                     (.withContentDisposition (ResponseHeaderOverrides.)
                                              content-disposition))
                    request)]
      (str (s3/generate-presigned-url request))))

  (def download-url (partial presigned-url "GET"))
  (def upload-url (partial presigned-url "PUT" nil)))

(defn download-url [content-disposition file-name]
  (integration-s3/presigned-url content-disposition
                                "GET" (storage-bucket) file-name))

(defn upload-url [file-name]
  (integration-s3/presigned-url "PUT" (storage-bucket) file-name))

(defn document-s3-ref
  "Returns an integration S3 file descriptor for a document."
  [{id :db/id
    name :file/name}]
  {:bucket (storage-bucket)
   :file-key (str id "-" name)})
