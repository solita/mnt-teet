(ns teet.document.document-commands
  (:require [teet.db-api.core :as db-api]
            [amazonica.aws.s3 :as s3]
            [clojure.spec.alpha :as s]
            [datomic.client.api :as d])
  (:import (java.util Date)))

(def ^:const upload-url-expiration-ms 15000)
(def ^:const upload-max-file-size (* 1024 1024 100))
(def ^:const upload-allowed-file-types #{"image/png" "application/pdf" "application/zip"})

(defn validate-document [{:document/keys [type size]}]
  (cond
    (> size upload-max-file-size)
    {:error :file-too-large :max-allowed-size upload-max-file-size}

    (not (upload-allowed-file-types type))
    {:error :file-type-not-allowed :allowed-types upload-allowed-file-types}

    :else
    nil))

(defmethod db-api/command! :document/upload [{conn :conn} {task-id :task-id :as document}]
  (let [doc (merge {:db/id "doc"}
                   (dissoc document :task-id))
        res (d/transact conn {:tx-data [(if task-id
                                          ;; Task id specified, add this document in the task
                                          {:db/id task-id
                                           :task/documents [doc]}

                                          ;; No task id specified
                                          doc)]})
        bucket "teet-dev-documents" ;; FIXME: get from environment
        doc-id (get-in res [:tempids "doc"])
        key (str doc-id "-" (:document/name document))]

    (or (validate-document document)
        {:url (str (s3/generate-presigned-url {:bucket-name bucket
                                               :key key
                                               :method "PUT"
                                               :expiration (Date. (+ (System/currentTimeMillis)
                                                                     upload-url-expiration-ms))}))
         :document (d/pull (:db-after res) '[*] doc-id)})))
