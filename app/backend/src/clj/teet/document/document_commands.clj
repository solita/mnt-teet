(ns teet.document.document-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.document.document-storage :as document-storage]
            teet.document.document-spec
            [clojure.string :as str]
            [teet.meta.meta-model :refer [modification-meta creation-meta deletion-tx]])
  (:import (java.util Date)))


(def ^:const upload-max-file-size (* 1024 1024 100))
(def ^:const upload-allowed-file-types #{"image/png"
                                         "image/jpeg"
                                         "application/pdf"
                                         "application/zip"
                                         "text/ags"})

(def ^:const upload-file-suffix-type {"ags" "text/ags"})

(defn- type-by-suffix [{:file/keys [name type] :as file}]
  (if-not (str/blank? type)
    file
    (if-let [type-by-suffix (-> name (str/split #"\.") last upload-file-suffix-type)]
      (assoc file :file/type type-by-suffix)
      file)))

(defn validate-file [{:file/keys [type size]}]
  (cond
    (> size upload-max-file-size)
    {:error :file-too-large :max-allowed-size upload-max-file-size}

    (not (upload-allowed-file-types type))
    {:error :file-type-not-allowed :allowed-types upload-allowed-file-types}

    ;; No problems, upload can proceed
    :else
    nil))

;; Create new document and link it to task. Returns entity id for document.
(defmethod db-api/command! :document/new-document [{conn :conn} {:keys [task-id document]}]
  (println "new document")
  (-> conn
      (d/transact {:tx-data [(merge {:db/id "new-document"}
                                    (select-keys document [:document/name :document/status :document/description]))
                             {:db/id task-id
                              :task/documents [{:db/id "new-document"}]}
                             ]})
      (get-in [:tempids "new-document"])))

(defmethod db-api/command! :document/update-document [{conn :conn
                                                       user :user} document]
  (select-keys
    (d/transact conn {:tx-data [(assoc document :document/modified (Date.))
                                {:db/id "datomic.tx"
                                 :tx/author (:user/id user)}]})
    [:tempids]))

(defmethod db-api/command! :document/upload-file [{conn :conn
                                                   user :user}
                                                  {:keys [document-id file]}]
  (let [file (type-by-suffix file)]
    (or (validate-file file)
        (let [res (d/transact conn {:tx-data [{:db/id (or document-id "new-document")
                                               :document/files (merge file
                                                                      {:db/id "new-file"
                                                                       :file/author [:user/id (:user/id user)]
                                                                       :file/timestamp (java.util.Date.)})}
                                              {:db/id "datomic.tx"
                                               :tx/author (:user/id user)}]})
              doc-id (or document-id (get-in res [:tempids "new-document"]))
              file-id (get-in res [:tempids "new-file"])
              key (str file-id "-" (:file/name file))]

          {:url (document-storage/upload-url key)
           :document-id doc-id
           :file (d/pull (:db-after res) '[*] file-id)}))))

(defmethod db-api/command! :document/comment [{conn :conn
                                               user :user}
                                              {:keys [document-id comment]}]
  (-> conn
      (d/transact {:tx-data [{:db/id document-id
                              :document/comments [{:db/id "new-comment"
                                                   :comment/author [:user/id (:user/id user)]
                                                   :comment/comment comment
                                                   :comment/timestamp (java.util.Date.)}]}]})
      (get-in [:tempids "new-comment"])))

(defmethod db-api/command! :document/comment-on-file [{conn :conn
                                                       user :user}
                                                      {:keys [file-id comment]}]
  (-> conn
      (d/transact {:tx-data [{:db/id file-id
                              :file/comments [{:db/id "new-comment"
                                               :comment/author [:user/id (:user/id user)]
                                               :comment/comment comment
                                               :comment/timestamp (java.util.Date.)}]}]})
      (get-in [:tempids "new-comment"])))


(defmethod db-api/command! :document/delete [{conn :conn
                                              user :user}
                                             {:keys [document-id]}]
  (d/transact
    conn
    {:tx-data [(deletion-tx user document-id)]})
  :ok)
