(ns teet.integration.integration-s3
  "Integration steps for S3"
  (:require [amazonica.aws.s3 :as s3]
            [clojure.spec.alpha :as s]
            [teet.integration.integration-context :refer [defstep]]
            [cheshire.core :as cheshire]))

(defn- bucket-and-key [s3-data]
  {:bucket   (get-in s3-data [:bucket :name])
   :file-key (get-in s3-data [:object :key])})

(defn- s3-file-data [input]
  (-> input :Records first :s3 bucket-and-key))

(defn- valid-file-descriptor? [{:keys [bucket file-key]}]
  (and (string? bucket)
       (string? file-key)))

(s/def ::file-descriptor valid-file-descriptor?)
(s/def ::file-contents #(instance? java.io.InputStream %))

(s/def ::s3-trigger-event (s/keys :req-un [:s3-trigger-event/input]))
(s/def :s3-trigger-event/input string?)

(defstep read-trigger-event
  {:doc "Read file information from an S3 lambda trigger event"
   :in {lambda-event {:spec ::s3-trigger-event
                      :path-kw :event
                      :default-path [:event]}}
   :out {:spec ::file-descriptor
         :default-path [:s3]}}
  (-> lambda-event :input
      (cheshire/decode keyword)
      s3-file-data))

(defstep load-file-from-s3
  {:ctx ctx
   :doc "Load file from S3. Result is an input stream."
   :in {fd {:spec ::file-descriptor
            :path-kw :s3
            :default-path [:s3]}}
   :out {:spec ::file-contents
         :default-path [:file]}}
  (let [{:keys [bucket file-key]} fd]
    (:input-stream
     (s3/get-object bucket file-key))))

(defstep write-file-to-s3
  {:ctx ctx
   :doc "Write file to S3."
   :in {fd {:spec ::file-descriptor
            :path-kw :to
            :default-path [:to]}
        contents {:spec ::file-contents
                  :path-kw :contents
                  :default-path [:contents]}}
   :out {:spec some?
         :default-path [:write-result]}}
  (s3/put-object :bucket-name (:bucket fd)
                 :key (:file-key fd)
                 :input-stream contents))
