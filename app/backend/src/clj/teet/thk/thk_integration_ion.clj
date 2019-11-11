(ns teet.thk.thk-integration-ion
  "THK integration lambdas"
  (:require [amazonica.aws.s3 :as s3]
            [cheshire.core :as cheshire]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [teet.log :as log]))

(def test-event
  {:input "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"teet-dev-csv-import\"},\"object\":{\"key\":\"thk/unprocessed/hep.csv\"}}}]}"})

(def processed-directory "thk/processed/")
(def error-directory "thk/error/")
(def log-directory "thk/log/")

(defn ctx-exception [ctx message & [e]]
  (ex-info message
           (assoc ctx :error (str message
                                  (when e
                                    (str ": "
                                         (.getMessage e)))))))

(defn- decode-input [{:keys [event] :as ctx}]
  (try
    (assoc ctx :input (cheshire/decode (:input event) keyword))
    (catch Exception e
      (throw (ctx-exception ctx "Failed to decode input" e)))))

(defn bucket-and-key [s3-data]
  {:bucket   (get-in s3-data [:bucket :name])
   :file-key (get-in s3-data [:object :key])})

(defn- s3-file-data [{:keys [input] :as ctx}]
  (try
    (->> input
        :Records
        first
        :s3
        bucket-and-key
        (assoc ctx :s3))
    (catch Exception e
      (throw (ctx-exception ctx "Failed to get S3 file data" e)))))

(defn- load-file-from-s3 [{{:keys [bucket file-key]} :s3 :as ctx}]
  (try
    (->> (s3/get-object bucket file-key)
         :input-stream
         (assoc ctx :file))
    (catch Exception e
      (throw (ctx-exception ctx "Failed to load file from S3" e)))))

(defn- file->csv [{:keys [file] :as ctx}]
  (try
    (assoc ctx :csv
           (-> file
               (io/reader :encoding "UTF-8")
               (csv/read-csv :separator \,)))
    (catch Exception e
      (throw (ctx-exception ctx "Failed to parse CSV file" e))))) ;; TODO: THK import uses ; as separator

(defn- csv->updates [ctx]
  ;; TODO: create an update data structure that describes necessary updates
  ctx)

(defn- run-updates [ctx]
  ;; TODO: update the projects either in Datomic or Postgres
  (throw (ctx-exception ctx "Failed to run updates"))
  )

(defn- move-file [bucket old-key new-key]
  (s3/copy-object bucket old-key bucket new-key)
  (s3/delete-object :bucket-name bucket :key old-key))

(defn- change-directory [file-key directory]
  (str directory
       (-> file-key (str/split #"/") last)))

(defn- move-file-to-processed [{{:keys [bucket file-key]} :s3 :as ctx}]
  (try
    (let [processed-file-key (change-directory file-key processed-directory)]
      (move-file bucket file-key processed-file-key))
    (catch Exception e
      (throw (ctx-exception ctx "Failed to move file to processed directory" e)))))

(defn- move-file-to-error [{{:keys [bucket file-key]} :s3}]
  (let [processed-file-key (change-directory file-key error-directory)]
    (move-file bucket file-key processed-file-key)))

(defn- write-error-to-log [{{:keys [bucket file-key]} :s3
                            error :error}]
  (log/info bucket " " file-key " " error)
  (s3/put-object :bucket-name bucket
                 :key (change-directory file-key log-directory)
                 :input-stream (io/input-stream (.getBytes (str error)))))

(defn- on-error [{:keys [error] :as ctx}]
  ;; TODO: Metrics?
  (log/error error)
  (move-file-to-error ctx)
  (write-error-to-log ctx))

(defn process-thk-file
  [event]
  (try
    (let [result (-> {:event event}
                     decode-input
                     s3-file-data
                     load-file-from-s3
                     file->csv
                     csv->updates
                     run-updates
                     move-file-to-processed)]
      (log/event :thk-file-processed
                 {:input result}))
    (catch Exception e
      (on-error (ex-data e)))))
