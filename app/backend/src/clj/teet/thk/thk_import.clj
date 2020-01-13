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

(defn thk-activity-type->activity-name [act_typefk]
  (case act_typefk
    ;; THK has activities without mapping in TEET,
    ;; we skip those as they should be removed in
    ;; the future.
    ;;
    ;; 4000 Planeering
    ;; 4001 Uuring/Anal
    ;; 4002 KMH
    "4003" :activity.name/detailed-design ;; Pöhiprojekt
    "4004" :activity.name/land-acquisition ;; Maaost
    "4005" :activity.name/construction ;; Teostus
    ;; 4010 ekspertiis
    ;; 4011 LOA proj
    "4012" :activity.name/pre-design ;; Eskiisproj
    "4013" :activity.name/preliminary-design ;; Eelproj

    ;; Default to other
    nil))

(defn thk-activity-status->status [act_statusfk]
  (case act_statusfk
    ;;  4100 Ettevalmistamisel
    ;;  4101 Hankemenetluses
    "4102" :activity.status/in-progress ;; Töös
    ;;  4103 Garantiiaeg
    "4104" :activity.status/completed ;; Lõpetatud
    ;;  4106 Hankeplaanis
    ;; Unmapped status
    :activity.status/other))

(defn project-datomic-attributes [[project-id rows]]
  (let [prj (first rows)
        phases (group-by #(get % "ph_id") rows)
        phase-est-starts (map (comp ->date #(get % "ph_estStart")) rows)
        phase-est-ends (map (comp ->date #(get % "ph_estEnd")) rows)

        project-est-start (first (sort phase-est-starts))
        project-est-end (last (sort phase-est-ends))]
    (when-not (excluded-project-types (prj "shortname"))
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
              (for [[id activities] phases
                    :let [{:strs [ph_shname ; phase type
                                  ph_estStart ph_estEnd]}  ; estimated start/end dates
                          (first activities)]]
                (merge
                 {:db/id id
                  :thk.lifecycle/id id
                  :thk.lifecycle/type
                  (case ph_shname
                    "projetapp" :thk.lifecycle-type/design
                    "ehitetapp" :thk.lifecycle-type/construction)
                  :thk.lifecycle/estimated-start-date (->date ph_estStart)
                  :thk.lifecycle/estimated-end-date (->date ph_estEnd)
                  :thk.lifecycle/activities
                  (for [{:strs [act_id ; THK id
                                act_teetid ; TEET id (if known by THK)
                                act_typefk act_shname ; activity type
                                act_statusfk act_statname ; status
                                act_estStart act_estEnd]} activities
                        :let [activity-name (thk-activity-type->activity-name act_typefk)]
                        :when (and act_id activity-name)]
                    {:thk.activity/id act_id
                     :db/id act_id
                     :activity/estimated-start-date (->date act_estStart)
                     :activity/estimated-end-date (->date act_estEnd)
                     :activity/name activity-name
                     :activity/status (thk-activity-status->status act_statusfk) })})))}))))

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
