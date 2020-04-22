(ns teet.file.file-import
  "Post process uploaded files and import features from them."
  (:require [teet.ags.ags-import :as ags-import]
            [teet.integration.integration-context :refer [ctx-> defstep]]
            [teet.integration.integration-s3 :as integration-s3]
            [clojure.string :as str]
            [teet.log :as log]
            [teet.environment :as environment]
            [teet.project.project-model :as project-model]
            [datomic.client.api :as d]))

(defmulti import-by-suffix
  "Import file based on suffix"
  (fn [ctx]
    (-> ctx :s3 :file-key
        (str/split #"\.")
        last
        str/lower-case)))

(defmethod import-by-suffix :default
  [ctx]
  (log/info "Nothing to import for uploaded file: " (:s3 ctx)))

(defmethod import-by-suffix "ags" [ctx]
  (ags-import/import-project-ags-files ctx))

(defstep extract-project-from-filename
  {:doc "Extract project by looking it up from file id"
   :in {fd {:spec ::integration-s3/file-descriptor
            :default-path [:s3]
            :path-kw :s3}
        conn {:spec some?
              :default-path [:conn]
              :path-kw :conn}}
   :out {:spec ::project-model/id
         :default-path [:project]}}
  (let [[_ file-id-part] (re-find #"^(\d+)-.*$" (:file-key fd))
        file-id (Long/parseLong file-id-part)
        file (du/entity (d/db conn) file-id)
        project-id (get-in file [:task/_files 0
                                 :activity/_tasks 0
                                 :thk.lifecycle/_activities 0
                                 :thk.project/_lifecycles 0
                                 :db/id])]
    (when-not project-id
      (throw (ex-info "Unable to determine project for uploaded file"
                      {:file-descriptor fd})))
    (log/info "Uploaded file" (:file-key fd) "belongs to project" project-id)
    project-id))

(defn import-uploaded-file [event]
  (ctx-> {:event event
          :conn (environment/datomic-connection)
          :api-url (environment/config-value :api-url)
          :api-secret (environment/config-value :auth :jwt-secret)}
         integration-s3/read-trigger-event
         extract-project-from-filename
         import-by-suffix)
  "ok")
