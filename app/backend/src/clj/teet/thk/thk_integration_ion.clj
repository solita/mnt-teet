(ns teet.thk.thk-integration-ion
  "THK integration lambdas"
  (:require [amazonica.aws.s3 :as s3]
            [cheshire.core :as cheshire]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [teet.log :as log]))

(def test-input "{\"Records\":[1,2,3]}")

(def test-event
  {:input test-input})

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

(defn load-file-from-s3 [{{:keys [bucket file-key]} :s3 :as ctx}]
  (->> (s3/get-object bucket file-key)
       :input-stream
       (assoc ctx :file)))

(defn file->csv [{:keys [file] :as ctx}]
  (assoc ctx :csv
         (-> file
             (io/reader :encoding "UTF-8")
             (csv/read-csv :separator \,)))) ;; TODO: THK import uses ; as separator

(defn csv->updates [ctx]
  ;; TODO create an update data structure that describes necessary updates
  ctx)

(defn run-updates [ctx]
  ;; TODO update the projects either in Datomic or Postgres
  ctx)

(defn move-file-to-processed [ctx]
  ;; TODO need to thread necessary file data though the functions, add context as first argument?
  (:s3 ctx))

(defn process-thk-file
  [event]
  (let [result (-> {:event event}
                   decode-input
                   s3-file-data
                   load-file-from-s3
                   file->csv
                   csv->updates
                   run-updates
                   move-file-to-processed)]
    (log/event :thk-file-processed
               {:input result})
    result))
