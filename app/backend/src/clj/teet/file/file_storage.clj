(ns teet.file.file-storage
  "Access to S3 bucket for storing files. Access must be checked in previous layers."
  (:require [amazonica.aws.s3 :as s3]
            [teet.environment :as environment])
  (:import (java.util Date)))

(def ^:const url-expiration-ms 300000)

(defn- expiration-date []
  (Date. (+ (System/currentTimeMillis) url-expiration-ms)))

(defn- storage-bucket []
  (environment/config-value :document-storage :bucket-name))

(defn- presigned-url [method file-name]
  (str (s3/generate-presigned-url
        {:bucket-name (storage-bucket)
         :key file-name
         :method method
         :expiration (expiration-date)})))

(def download-url (partial presigned-url "GET"))
(def upload-url (partial presigned-url "PUT"))

(defn document-s3-ref
  "Returns an integration S3 file descriptor for a document."
  [{id :db/id
    name :file/name}]
  {:bucket (storage-bucket)
   :file-key (str id "-" name)})
