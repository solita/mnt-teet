(ns teet.thk.thk-integration-ion
  "THK integration lambdas"
  (:require [amazonica.aws.s3 :as s3]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [teet.log :as log]
            [teet.thk.thk-import :as thk-import]
            [teet.thk.thk-export :as thk-export]
            [teet.environment :as environment]
            [teet.integration.integration-context :as integration-context :refer [ctx->]]
            [teet.integration.integration-s3 :as integration-s3]
            [datomic.client.api :as d]
            [clojure.data.csv :as csv]
            [teet.project.project-geometry :as project-geometry]))

(def test-event
  {:input "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"teet-dev-csv-import\"},\"object\":{\"key\":\"thk/unprocessed/not_hep.csv\"}}}]}"})

(def processed-directory "thk/processed/")
(def error-directory "thk/error/")
(def log-directory "thk/log/")


(defn- write-file-to-s3 [{{:keys [bucket-name file-key]} :s3
                          file :file
                          :as ctx}]
  (let [response
        (s3/put-object :bucket-name bucket-name
                       :key file-key
                       :input-stream (io/input-stream file))]
    (if-not (contains? response :content-md5)
      (throw (ex-info "Expected S3 write response to contain :content-md5"
                      {:s3-response response}))
      (assoc ctx file-key response))))

(defn- file->csv [{:keys [file] :as ctx}]
  (assoc ctx :csv
         (thk-import/parse-thk-export-csv file)))

(defn- csv->file [{csv :csv :as ctx}]
  (with-open [baos (java.io.ByteArrayOutputStream.)
              writer (java.io.OutputStreamWriter. baos "UTF-8")]
    ;; Write UTF-8 byte order mark
    (.write baos (int 0xEF))
    (.write baos (int 0xBB))
    (.write baos (int 0xBF))
    (csv/write-csv writer csv :separator \;)
    (.flush writer)
    (assoc ctx :file (.toByteArray baos))))

(defn- upsert-projects [{:keys [bucket file-key csv connection] :as ctx}]
  (let [import-tx-result (thk-import/import-thk-projects! connection
                                                          (str "s3://" bucket "/" file-key)
                                                          csv)]
    (assoc ctx
           :db (:db-after import-tx-result))))

(defn- move-file [bucket old-key new-key]
  (s3/copy-object bucket old-key bucket new-key)
  (s3/delete-object {:bucket-name bucket :key old-key}))

(defn- change-directory [file-key directory]
  (str directory
       (-> file-key (str/split #"/") last)))

(defn- add-suffix [file-key suffix]
  (str file-key suffix))

(defn- move-file-to-processed [{{:keys [bucket file-key]} :s3 :as ctx}]
  (try
    (let [processed-file-key (-> file-key
                                 (change-directory processed-directory)
                                 (add-suffix (str "." (System/currentTimeMillis))))]
      (move-file bucket file-key processed-file-key))
    (catch Exception e
      (throw (integration-context/ctx-exception ctx "Failed to move file to processed directory" e)))))

(defn- move-file-to-error [{{:keys [bucket file-key]} :s3 :as _ctx}]
  (let [processed-file-key (-> file-key
                               (change-directory error-directory)
                               (add-suffix (str "." (System/currentTimeMillis))))]
    (move-file bucket file-key processed-file-key)))

(defn- write-error-to-log [{{:keys [bucket file-key]} :s3
                            {:keys [message stack-trace]}  :error}]
  (s3/put-object {:bucket-name  bucket
                  :key          (-> file-key
                                    (change-directory log-directory)
                                    (add-suffix (str (System/currentTimeMillis)
                                                     ".error.txt")))
                  :input-stream (io/input-stream (.getBytes (str message
                                                                 "\n"
                                                                 stack-trace)))}))

(defn- update-entity-info [{db :db :as ctx}]
  (let [projects (map first
                      (d/q '[:find (pull ?e [:db/id :thk.project/name :thk.project/road-nr
                                             :thk.project/carriageway
                                             :thk.project/start-m :thk.project/end-m
                                             :thk.project/custom-start-m :thk.project/custom-end-m])
                             :in $
                             :where [?e :thk.project/road-nr _]]
                           db))]
    (log/info "Update entity info for all" (count projects) "projects.")
    (project-geometry/update-project-geometries! (select-keys ctx [:api-url :api-shared-secret])
                                                 projects)
    ctx))

(defn- on-import-error [{:keys [error] :as ctx}]
  ;; TODO: Metrics?
  (log/error error)
  (move-file-to-error ctx)
  (write-error-to-log ctx)
  nil)

(defn process-thk-file
  [event]
  (try
    (let [result (ctx-> {:event event
                         :connection (environment/datomic-connection)
                         :api-url (environment/config-value :api-url)
                         :api-shared-secret (environment/config-value :auth :jwt-secret)}
                        integration-s3/read-trigger-event
                        integration-s3/load-file-from-s3
                        file->csv
                        upsert-projects
                        update-entity-info
                        move-file-to-processed)]
      (log/event :thk-file-processed
                 {:input result}))
    (catch Exception e
      (log/error (.getCause e) "Exception in THK import")
      (on-import-error (ex-data e)))))

(defn import-thk-local-file
  [filepath]
  (try
    (ctx-> {:file (io/input-stream (io/file filepath))
            :connection (environment/datomic-connection)
            :api-url (environment/config-value :api-url)
            :api-shared-secret (environment/config-value :auth :jwt-secret)}
           file->csv
           upsert-projects
           update-entity-info)
    (catch Exception e
      (println e "Exception in import"))))

(defn export-projects [{conn :connection :as ctx}]
  (assoc ctx :csv (thk-export/export-thk-projects conn)))

(defn export-projects-to-thk
  [_event] ; ignore event (cron lambda trigger with no payload)
  (try
    (ctx-> {:connection (environment/datomic-connection)
            :s3 {:bucket-name (environment/config-value :thk :export-bucket-name)
                 :file-key (str (environment/config-value :thk :export-dir)
                                "/TEET_THK_"
                                (.format (java.text.SimpleDateFormat. "yyyyMMdd_HHmm") (java.util.Date.))
                                ".csv")}}
           export-projects
           csv->file
           write-file-to-s3)
    (catch Exception e
      (log/error "Error exporting projects CSV to S3: " (pr-str (ex-data e))))))
