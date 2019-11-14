(ns teet.thk.thk-import
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [taoensso.timbre :as log]
            [teet.util.datomic :as du])
  (:import (org.apache.commons.io.input BOMInputStream)
           (java.text SimpleDateFormat)))

(def excluded-project-types #{"TUGI"})

(defn parse-thk-export-csv [input]
  (with-open [raw-input-stream (io/input-stream input)
              input-stream (BOMInputStream. raw-input-stream)]
    (let [[headers extra-header & rows]
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

(defn ->int [str]
  (when-not (str/blank? str)
    (Integer/parseInt str)))

(defn ->num [str]
  (when-not (str/blank? str)
    (BigDecimal. str)))



;; TODO: Check that more recent CSV has the same columns
;; TODO: Add project schema to resources/schema.edn
;; TODO: Change Postgres function below to only add id and geometry
(defn import-thk-projects! [connection url projects]
  (let [date-format (SimpleDateFormat. "yyyy-MM-dd")
        ->date #(some->> %
                         (.parse date-format))
        without-nils #(reduce-kv
                        (fn [m k v]
                          (if (some? v)
                            (assoc m k v)
                            m))
                        {}
                        %)]
    (d/transact
     connection
     {:tx-data (into [{:db/id                  "datomic.tx"
                       :integration/source-uri url}]
                     (for [prj projects
                           :when (and            ;;FIXME: Do we need non road projcets?
                                  (not (str/blank? (prj "PlanObject.KmStart")))
                                  (not (excluded-project-types (prj "PlanObject.PlanGroupFK"))))]
                       (without-nils
                        {:thk.project/id                   (prj "PlanObject.Id")
                         :thk.project/road-nr              (->int (prj "PlanObject.RoadNr"))
                         :thk.project/bridge-nr            (->int (prj "PlanObject.BridgeNr"))

                         :thk.project/start-m              (int (* 1000 (->num (prj "PlanObject.KmStart"))))
                         :thk.project/end-m                (int (* 1000 (->num (prj "PlanObject.KmEnd"))))
                         :thk.project/carriageway          (->int (prj "PlanObject.Carriageway"))

                         :thk.project/name                 (prj "PlanObject.ObjectName")

                         :thk.project/procurement-nr       (prj "Procurement.ProcurementNo")
                         :thk.project/procurement-id       (->int (prj "Procurement.ID"))
                         :thk.project/estimated-start-date (->date (prj "Activity.EstStart"))
                         :thk.project/estimated-end-date   (->date (prj "Activity.EstEnd"))})))})))
