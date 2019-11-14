(ns teet.thk.thk-integration-ion
  "THK integration lambdas"
  (:require [amazonica.aws.s3 :as s3]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.stacktrace :as stacktrace]
            [clojure.string :as str]
            [teet.log :as log]
            [teet.thk.thk-import :as thk-import]
            [teet.util.datomic :as du]
            [teet.environment :as environment]
            [datomic.client.api :as d]
            [org.httpkit.client :as client]
            [teet.login.login-api-token :as login-api-token]))

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

(defn- file->csv [{:keys [file] :as ctx}]
  (assoc ctx :csv
         (thk-import/parse-thk-export-csv file)))

(defn- upsert-projects [{:keys [bucket file-key csv connection] :as ctx}]
  (let [import-tx-result (thk-import/import-thk-projects! connection
                                                          (str "s3://" bucket "/" file-key)
                                                          csv)]
    (assoc ctx
           :changed-entity-ids (du/changed-entity-ids import-tx-result)
           :db (:db-after import-tx-result))))

(defn- move-file [bucket old-key new-key]
  (s3/copy-object bucket old-key bucket new-key)
  (s3/delete-object :bucket-name bucket :key old-key))

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
  (s3/put-object :bucket-name bucket
                 :key (-> file-key
                          (change-directory log-directory)
                          (add-suffix (str (System/currentTimeMillis)
                                           ".error.txt")))
                 :input-stream (io/input-stream (.getBytes (str message
                                                                "\n"
                                                                stack-trace)))))

(defn- update-entity-info [{:keys [changed-entity-ids db api-url api-shared-secret]}]
  (let [updated-projects (d/q '[:find (pull ?e [:db/id :thk.project/name :thk.project/road-nr :thk.project/carriageway
                                                :thk.project/start-m :thk.project/end-m])
                                :in $ [?e ...]
                                :where [?e :thk.project/road-nr _]]
                              db changed-entity-ids)]
    (log/info "Update entity info for" (count changed-entity-ids) "projects.")
    @(client/post api-url
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
                             :tooltip name}))})))

(defn- on-error [{:keys [error] :as ctx}]
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
      (on-error (ex-data e)))))
