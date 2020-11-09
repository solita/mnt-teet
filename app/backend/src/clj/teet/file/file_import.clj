(ns teet.file.file-import
  "Post process uploaded files and import features from them."
  (:require [teet.ags.ags-import :as ags-import]
            [teet.integration.integration-context :refer [ctx-> defstep]]
            [teet.integration.integration-s3 :as integration-s3]
            [clojure.string :as str]
            [teet.log :as log]
            [teet.environment :as environment]
            [teet.project.project-model :as project-model]
            [datomic.client.api :as d]
            [teet.util.datomic :as du]))

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

(defn- file-key->project-id [db file-key]
  (ffirst
   (d/q '[:find ?project
          :in $ ?file-key
          :where
          [?file :file/s3-key ?file-key]
          [?task :task/files ?file]
          [?activity :activity/tasks ?task]
          [?lifecycle :thk.lifecycle/activities ?activity]
          [?project :thk.project/lifecycles ?lifecycle]]
        db file-key)))

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
  (let [project-id (file-key->project-id (d/db conn) (:file-key fd))]
    (log/info "Uploaded file" (:file-key fd) "belongs to project" project-id)
    project-id))

(defn import-file [ctx]
  (when (:project ctx)
    (import-by-suffix ctx))
  ctx)

(defn import-uploaded-file [event]
  (future
    (ctx-> {:event event
            :conn (environment/datomic-connection)
            :api-url (environment/config-value :api-url)
            :api-secret (environment/config-value :auth :jwt-secret)}
           integration-s3/read-trigger-event
           extract-project-from-filename
           import-file))
  "{\"success\": true}")
