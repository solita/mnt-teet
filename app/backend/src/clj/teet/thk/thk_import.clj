(ns teet.thk.thk-import
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [teet.util.collection :as cu])
  (:import (org.apache.commons.io.input BOMInputStream)
           (java.text SimpleDateFormat)))

(def excluded-project-types #{"TUGI" "TEEMU"})

(defn parse-thk-export-csv [input]
  (with-open [raw-input-stream (io/input-stream input)
              input-stream (BOMInputStream. raw-input-stream)]
    (let [[headers & rows]
          (-> input-stream
              (io/reader :encoding "UTF-8")
              (csv/read-csv :separator \;))]
      ;; There are multiple rows per project. One for each project lifecycle phase.
      (group-by #(get % "ob_id")
                (map #(zipmap headers %) rows)))))

(defn blank? [s]
  (or (not s)
      (str/blank? s)))

(defn ->int [str]
  (when-not (blank? str)
    (Integer/parseInt str)))

(defn ->num [str]
  (when-not (blank? str)
    (BigDecimal. str)))

(defn- ->date [date-str]
  (when-not (blank? date-str)
    (.parse (SimpleDateFormat. "yyyy-MM-dd")
            date-str)))

(defn- km->m  [km]
  (when km
    (int (* 1000 km))))

(defn project-datomic-attributes [[project-id lifecycle-phases]]
  (let [prj (first lifecycle-phases)
        phase-est-starts (map (comp ->date #(get % "est_start")) lifecycle-phases)
        phase-est-ends (map (comp ->date #(get % "est_end")) lifecycle-phases)

        project-est-start (first (sort phase-est-starts))
        project-est-end (last (sort phase-est-ends))]
    (cu/without-nils
     {:db/id project-id
      :thk.project/id project-id
      :thk.project/road-nr              (->int (prj "roadnr"))
      :thk.project/bridge-nr            (->int (prj "bridgenr"))
      :thk.project/start-m              (km->m (->num (prj "kmstart")))
      :thk.project/end-m                (km->m (->num (prj "kmend")))
      :thk.project/carriageway          (->int (prj "carriageway"))
      :thk.project/name                 (prj "objectname")
      :thk.project/estimated-start-date project-est-start
      :thk.project/estimated-end-date   project-est-end

      :thk.project/lifecycles
      (into []
            (for [{:strs [est_start est_end phase]} lifecycle-phases
                  ;; PENDING: should come from CSV field
                  :let [id (str project-id "-" phase)]]
              {:db/id id
               :thk.lifecycle/id id
               :thk.lifecycle/type
               (case phase
                 "ehitus" :thk.lifecycle-type/design
                 "projekteerimine" :thk.lifecycle-type/construction)
               :thk.lifecycle/estimated-start-date (->date est_start)
               :thk.lifecycle/estimated-end-date (->date est_end)}))})))

(defn teet-project? [[_ [p1 & _]]]
  (and p1
       (not (blank? (p1 "kmstart")))
       (not (excluded-project-types (p1 "thk_grupp")))))

(defn- thk-project-tx [url projects-csv]
  (into [{:db/id "datomic.tx"
          :integration/source-uri url}]
        (for [prj projects-csv
              :when (teet-project? prj)]
          (project-datomic-attributes prj))))

(defn import-thk-projects! [connection url projects]
  (d/transact connection
              {:tx-data (thk-project-tx url projects)}))
