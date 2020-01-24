(ns teet.document.document-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.document.document-storage :as document-storage]
            teet.document.document-spec
            [clojure.string :as str]
            [teet.meta.meta-model :refer [modification-meta creation-meta deletion-tx]]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du])
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
(defmethod db-api/command! :document/new-document [{conn :conn
                                                    user :user} {:keys [task-id document]}]
  (let [author-id (get-in document [:document/author :user/id])]
    (-> conn
        (d/transact {:tx-data [(merge {:db/id "new-document"}
                                      (cu/without-nils (select-keys document
                                                                    [:document/name :document/status :document/description :document/category :document/sub-category]))
                                      (when author-id
                                        {:document/author [:user/id author-id]})
                                      (creation-meta user))
                               {:db/id          task-id
                                :task/documents [{:db/id "new-document"}]}]})
        (get-in [:tempids "new-document"]))))

(defmethod db-api/command! :document/edit-document [{conn :conn
                                                     user :user}
                                                    {:keys [document]}]
  (let [author-id (get-in document [:document/author :user/id])]
    (d/transact conn {:tx-data
                      [(merge (cu/without-nils (select-keys document
                                                            [:db/id :document/name :document/status :document/description :document/category :document/sub-category]))
                              (when author-id
                                {:document/author [:user/id author-id]})
                              (modification-meta user))]}))
  :ok)

(defmethod db-api/command! :document/upload-file [{conn :conn
                                                   user :user}
                                                  {:keys [document-id file]}]
  (let [file (type-by-suffix file)]
    (or (validate-file file)
        (let [res (d/transact conn {:tx-data [{:db/id (or document-id "new-document")
                                               :document/files (merge file
                                                                      {:db/id "new-file"}
                                                                      (creation-meta user))}
                                              {:db/id "datomic.tx"
                                               :tx/author (:user/id user)}]})
              doc-id (or document-id (get-in res [:tempids "new-document"]))
              file-id (get-in res [:tempids "new-file"])
              key (str file-id "-" (:file/name file))]

          {:url (document-storage/upload-url key)
           :document-id doc-id
           :file (d/pull (:db-after res) '[*] file-id)}))))


(defmethod db-api/command! :document/delete [{conn :conn
                                              user :user}
                                             {:keys [document-id]}]
  (d/transact
    conn
    {:tx-data [(deletion-tx user document-id)]})
  :ok)

(defmethod db-api/command! :document/delete-file [{conn :conn
                                                   user :user}
                                                  {:keys [file-id]}]
  (d/transact
   conn
    {:tx-data [(deletion-tx user file-id)]})
  :ok)
