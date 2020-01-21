(ns teet.thk.thk-import
  "Import projects (object), lifecycles (phase) and activities from THK.
  THK provides a CSV file that is flat and contains information on the
  project, lifecycle and activity on the same row.

  Rows are parsed and grouped by the project id (\"object_id\" field).
  Project rows are further grouped by lifecycle id (\"phase_id\" field) to
  yield activity rows."
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [teet.util.collection :as cu]
            [teet.thk.thk-mapping :as thk-mapping])
  (:import (org.apache.commons.io.input BOMInputStream)))

(def excluded-project-types #{"TUGI" "TEEMU"})

(defn parse-thk-export-csv [input]
  (with-open [raw-input-stream (io/input-stream input)
              input-stream (BOMInputStream. raw-input-stream)]
    (let [[headers & rows]
          (-> input-stream
              (io/reader :encoding "UTF-8")
              (csv/read-csv :separator \;))]
      ;; There are multiple rows per project. One for each project lifecycle phase.
      (group-by #(get % :thk.project/id)
                (into []
                      (comp
                       (map #(zipmap headers %))
                       (map #(into {}
                                   (for [[header value] %
                                         :let [[teet-kw parse-fn] (thk-mapping/thk->teet header)]
                                         :when (and teet-kw
                                                    (not (str/blank? value)))]
                                     [teet-kw ((or parse-fn identity) value)]))))
                      rows)))))


(defn integration-info [row fields]
  (pr-str (select-keys row fields)))

(defn project-datomic-attributes [[project-id rows]]
  (let [prj (first rows)
        phases (group-by :thk.lifecycle/id rows)
        phase-est-starts (keep :thk.lifecycle/estimated-start-date rows)
        phase-est-ends (keep :thk.lifecycle/estimated-end-date rows)

        project-est-start (first (sort phase-est-starts))
        project-est-end (last (sort phase-est-ends))]
    (cu/without-nils
     (merge
      (select-keys prj #{:thk.project/id
                         :thk.project/road-nr
                         :thk.project/bridge-nr
                         :thk.project/start-m
                         :thk.project/end-m
                         :thk.project/carriageway
                         :thk.project/name})
      {:db/id project-id
       :thk.project/estimated-start-date project-est-start
       :thk.project/estimated-end-date project-est-end
       :thk.project/integration-info (integration-info
                                      prj thk-mapping/object-integration-info-fields)
       :thk.project/lifecycles
       (into []
             (for [[id activities] phases
                   :let [phase (first activities)]]
               (merge
                (select-keys phase #{:thk.lifecycle/type
                                     :thk.lifecycle/estimated-start-date
                                     :thk.lifecycle/estimated-end-date
                                     :thk.lifecycle/id})
                {:db/id id
                 :thk.lifecycle/integration-info (integration-info phase
                                                                   thk-mapping/phase-integration-info-fields)
                 :thk.lifecycle/activities
                 (for [{id :thk.activity/id
                        name :activity/name
                        :as activity} activities
                       :when (and id name)]
                   (merge
                    (select-keys activity #{:thk.activity/id
                                            :activity/estimated-start-date
                                            :activity/estimated-end-date
                                            :activity/name
                                            :activity/status})
                    {:db/id id
                     :activity/integration-info (integration-info
                                                 activity
                                                 thk-mapping/activity-integration-info-fields)}))})))}))))

(defn teet-project? [[_ [p1 & _]]]
  (and p1
       (:thk.project/start-m p1)
       (not (excluded-project-types (:object/groupname p1)))))

(defn- thk-project-tx [url projects-csv]
  (into [{:db/id "datomic.tx"
          :integration/source-uri url}]
        (for [prj projects-csv
              :when (teet-project? prj)]
          (project-datomic-attributes prj))))

(defn import-thk-projects! [connection url projects]
  (d/transact connection
              {:tx-data (thk-project-tx url projects)}))
