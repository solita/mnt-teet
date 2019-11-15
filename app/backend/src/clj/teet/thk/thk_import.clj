(ns teet.thk.thk-import
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.client.api :as d])
  (:import (org.apache.commons.io.input BOMInputStream)
           (java.text SimpleDateFormat)))

(def excluded-project-types #{"TUGI"})

(defn parse-thk-export-csv [input]
  (with-open [raw-input-stream (io/input-stream input)
              input-stream (BOMInputStream. raw-input-stream)]
    (let [[headers _ignore-extra-header & rows]
          (-> input-stream
              (io/reader :encoding "UTF-8")
              (csv/read-csv :separator \;))]
      ;; There are multiple rows per project. One for each financial year.
      ;; For now we only use the first one
      ;; TODO: model financial years in datomic
      (map first
           (vals
             (group-by
               #(get % "PlanObject.Id")
               (doall (map #(zipmap headers %) rows))))))))

(defn blank? [s]
  (or (not s)
      (str/blank? s)))

(defn ->int [str]
  (when-not (blank? str)
    (Integer/parseInt str)))

(defn ->num [str]
  (when-not (blank? str)
    (BigDecimal. str)))

;; TODO: Move to some util namespace
(defn without-nils [m]
  (reduce-kv (fn [m k v]
               (if (some? v)
                 (assoc m k v)
                 m))
             {}
             m))

(defn- ->date [date-str]
  (when-not (blank? date-str)
    (.parse (SimpleDateFormat. "yyyy-MM-dd")
            date-str)))

(defn- km->m  [km]
  (when km
    (int (* 1000 km))))

(defn project-datomic-attributes [csv-prj]
  (without-nils {:thk.project/id                   (csv-prj "PlanObject.Id")
                 :thk.project/road-nr              (->int (csv-prj "PlanObject.RoadNr"))
                 :thk.project/bridge-nr            (->int (csv-prj "PlanObject.BridgeNr"))

                 :thk.project/start-m              (km->m (->num (csv-prj "PlanObject.KmStart")))
                 :thk.project/end-m                (km->m (->num (csv-prj "PlanObject.KmEnd")))
                 :thk.project/carriageway          (->int (csv-prj "PlanObject.Carriageway"))

                 :thk.project/name                 (csv-prj "PlanObject.ObjectName")

                 :thk.project/procurement-nr       (csv-prj "Procurement.ProcurementNo")
                 :thk.project/procurement-id       (->int (csv-prj "Procurement.ID"))
                 :thk.project/estimated-start-date (->date (csv-prj "Activity.EstStart"))
                 :thk.project/estimated-end-date   (->date (csv-prj "Activity.EstEnd"))}))

(defn teet-project? [csv-prj]
  (and (not (blank? (csv-prj "PlanObject.KmStart")))
       (not (excluded-project-types (csv-prj "PlanObject.PlanGroupFK")))))

(defn import-thk-projects! [connection url projects]
  (d/transact
   connection
   {:tx-data (into [{:db/id "datomic.tx"
                     :integration/source-uri url}]
                   (for [prj projects
                         :when (teet-project? prj)]
                     (project-datomic-attributes prj)))}))
