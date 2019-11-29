(ns teet.thk.thk-integration-ion
  "THK integration lambdas"
  (:require [amazonica.aws.s3 :as s3]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [teet.log :as log]
            [teet.thk.thk-import :as thk-import]
            [teet.thk.thk-export :as thk-export]
            [teet.environment :as environment]
            [datomic.client.api :as d]
            [org.httpkit.client :as client]
            [teet.login.login-api-token :as login-api-token]
            [clojure.data.csv :as csv]))

(def test-event
  {:input "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"teet-dev-csv-import\"},\"object\":{\"key\":\"thk/unprocessed/not_hep.csv\"}}}]}"})

(def processed-directory "thk/processed/")
(def error-directory "thk/error/")
(def log-directory "thk/log/")

(defn- stack-trace [e]
  (with-out-str (stacktrace/print-stack-trace e 10)))

(defn ctx-exception [ctx message & [e]]
  (ex-info message
           (assoc ctx :error {:message (str message
                                            (when e
                                              (str ": "
                                                   (.getMessage e))))
                              :stack-trace (when e
                                             (stack-trace e))})))
(defmacro ctx->
  "Pipe ctx through steps, wrapping all steps in exception handling"
  [ctx & steps]
  `(-> ~ctx
       ~@(for [step steps]
           `((fn [~'ctx]
                (try
                  ~(if (list? step)
                     (concat (take 1 step)
                             (list 'ctx)
                             (drop 1 step))
                     (list step 'ctx))
                  (catch Exception e#
                    (throw (ctx-exception ~'ctx ~(or (:doc (meta step))
                                                     (str step)) e#)))))))))

(defn- decode-input [{:keys [event] :as ctx}]
  (assoc ctx :input (cheshire/decode (:input event) keyword)))

(defn bucket-and-key [s3-data]
  {:bucket   (get-in s3-data [:bucket :name])
   :file-key (get-in s3-data [:object :key])})

(defn- s3-file-data [{:keys [input] :as ctx}]
  (->> input
       :Records
       first
       :s3
       bucket-and-key
       (assoc ctx :s3)))

(defn- load-file-from-s3 [{{:keys [bucket file-key]} :s3 :as ctx}]
  (->> (s3/get-object bucket file-key)
       :input-stream
       (assoc ctx :file)))

(defn- write-file-to-s3 [{{:keys [bucket file-key file]} :s3 :as ctx}]
  (let [response
        (s3/put-object :bucket-name bucket
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
      (throw (ctx-exception ctx "Failed to move file to processed directory" e)))))

(defn- move-file-to-error [{{:keys [bucket file-key]} :s3 :as ctx}]
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

(defn- update-entity-info [{:keys [db api-url api-shared-secret] :as ctx}]
  (let [updated-projects (d/q '[:find (pull ?e [:db/id :thk.project/name :thk.project/road-nr
                                                :thk.project/carriageway
                                                :thk.project/start-m :thk.project/end-m])
                                :in $
                                :where [?e :thk.project/road-nr _]]
                              db)]
    (log/info "Update entity info for" (count updated-projects) "projects.")
    (let [response @(client/post (str api-url "/rpc/store_entity_info")
                                 {:headers {"Content-Type" "application/json"
                                            "Authorization" (str "Bearer " (login-api-token/create-backend-token
                                                                            api-shared-secret))}
                                  :body (cheshire/encode
                                         (for [{id :db/id
                                                :thk.project/keys [name road-nr carriageway start-m end-m]}
                                               (map first updated-projects)]
                                           {:id (str id)
                                            :type "project"
                                            :road road-nr
                                            :carriageway carriageway
                                            :start_m start-m
                                            :end_m end-m
                                            :tooltip name}))})]
      (when-not (= 200 (:status response))
        (throw (ex-info "store_entity_info RPC call failed"
                        {:response response})))
      (assoc ctx
             :entity-info-updated? true))))

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
                        decode-input
                        s3-file-data
                        load-file-from-s3
                        file->csv
                        upsert-projects
                        update-entity-info
                        move-file-to-processed)]
      (log/event :thk-file-processed
                 {:input result}))
    (catch Exception e
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
      (println "Got exception in import:" (type e))
      (ex-data e))))

(defn export-projects [{conn :connection :as ctx}]
  (assoc ctx :csv (thk-export/export-thk-projects conn)))

(defn export-projects-to-thk
  [_event] ; ignore event (cron lambda trigger with no payload)
  (try
    (ctx-> {:connection (environment/datomic-connection)
            :bucket (environment/config-value :thk :export-bucket-name)
            :file-key (str (environment/config-value :thk :export-dir)
                           "/projects-"
                           (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (java.util.Date.))
                           ".csv")}
           export-projects
           csv->file
           write-file-to-s3)
    (catch Exception e
      (log/error e "Error exporting projects CSV to S3"))))
