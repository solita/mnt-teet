(ns teet.document.document-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.document.document-storage :as document-storage])
  (:import (java.util Date)))


(def ^:const upload-max-file-size (* 1024 1024 100))
(def ^:const upload-allowed-file-types #{"image/png" "application/pdf" "application/zip"})

(defn validate-file [{:file/keys [type size]}]
  (cond
    (> size upload-max-file-size)
    {:error :file-too-large :max-allowed-size upload-max-file-size}

    (not (upload-allowed-file-types type))
    {:error :file-type-not-allowed :allowed-types upload-allowed-file-types}

    ;; No problems, upload can proceed
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
        doc-id (get-in res [:tempids "doc"])
        key (str doc-id "-" (:document/name document))]

    (or (validate-document document)
        {:url (document-storage/upload-url key)
         :document (d/pull (:db-after res) '[*] doc-id)})))

(defmethod db-api/command! :document/upsert-document [{conn :conn} doc]
  (select-keys
   (d/transact conn
               {:tx-data [doc]})
   [:tempids]))

(defmethod db-api/command! :document/link-to-task [{conn :conn} {:keys [task-id document-id]}]
  (d/transact {:tx-data [{:db/id task-id
                          :task/documents [{:db/id document-id}]}]}))

(defmethod db-api/command :document/upload-file [{conn :conn}
                                                 {:keys [document-id file]}]
  (or (validate-file file)
      (let [res (d/transact conn {:tx-data [{:db/id (or document-id "new-document")
                                             :document/files (assoc file {:db/id "new-file"})}]})
            doc-id (or document-id (get-in res [:tempids "new-document"]))
            file-id (get-in res [:tempids "new-file"])
            key (str doc-id "-" (:file/name file))]

        {:url (document-storage/upload-url key)
         :document-id doc-id
         :file (d/pull (:db-after res) '[*] file-id)})) )
