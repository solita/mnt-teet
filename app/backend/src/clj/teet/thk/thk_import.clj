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
            [teet.thk.thk-mapping :as thk-mapping]
            [teet.log :as log]
            [teet.project.project-db :as project-db])
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
                                         :let [{:keys [attribute parse]}
                                               (thk-mapping/thk->teet header)]
                                         :when (and attribute
                                                    (not (str/blank? value)))]
                                     [attribute ((or parse identity) value)]))))
                      rows)))))


(defn integration-info [row fields]
  (pr-str (select-keys row fields)))

(defn task-updates [rows]
  (into []
        (comp
         (filter #(some? (:activity/task-id %)))
         (map (fn [{:activity/keys [task-id] :as task}]
                (log/info "Received task with THK activity id "
                          (:thk.activity/id task)
                          " having TEET id " task-id)
                (cu/without-nils
                 {:integration/id task-id
                  :thk.activity/id (:thk.activity/id task)
                  :task/estimated-start-date (:activity/estimated-start-date task)
                  :task/estimated-end-date (:activity/estimated-end-date task)
                  :task/actual-start-date (:activity/actual-start-date task)
                  :task/actual-end-date (:activity/actual-end-date task)}))))
        rows))

(defn project-datomic-attributes [db [project-id rows]]
  (let [prj (first rows)
        phases (group-by :thk.lifecycle/id rows)
        phase-est-starts (keep :thk.lifecycle/estimated-start-date rows)
        phase-est-ends (keep :thk.lifecycle/estimated-end-date rows)

        project-est-start (first (sort phase-est-starts))
        project-est-end (last (sort phase-est-ends))
        project-exists? (project-db/project-exists? db project-id)
        project-has-owner? (and project-exists?
                                (project-db/project-has-owner? db [:thk.project/id project-id]))]
    (into
     [(cu/without-nils
       (merge
        (select-keys prj #{:thk.project/id
                           :thk.project/road-nr
                           :thk.project/bridge-nr
                           :thk.project/start-m
                           :thk.project/end-m
                           :thk.project/repair-method
                           :thk.project/region-name
                           :thk.project/carriageway
                           :thk.project/name})

        ;; Use THK provided owner only if project does not exist or doesn't have owner in TEET yet
        (when (or (not project-exists?)
                  (not project-has-owner?))
          (select-keys prj #{:thk.project/owner}))

        {:db/id (str "prj-" project-id)
         :thk.project/estimated-start-date project-est-start
         :thk.project/estimated-end-date project-est-end
         :thk.project/integration-info (integration-info
                                        prj thk-mapping/object-integration-info-fields)
         :thk.project/lifecycles
         (into []
               (for [[id activities] phases
                     :let [phase (first activities)]]
                 (cu/without-nils
                  (merge
                   (select-keys phase #{:thk.lifecycle/type
                                        :thk.lifecycle/estimated-start-date
                                        :thk.lifecycle/estimated-end-date
                                        :thk.lifecycle/id})
                   {:db/id (str "lfc-" id)
                    :thk.lifecycle/integration-info (integration-info phase
                                                                      thk-mapping/phase-integration-info-fields)
                    :thk.lifecycle/activities
                    (for [{id :thk.activity/id
                           name :activity/name
                           task-id :activity/task-id
                           :as activity} activities
                          ;; Only process activities here
                          :when (and id name (nil? task-id))
                          :let [{thk-id :thk.activity/id
                                 teet-id :activity-db-id} activity
                                _ (log/info "Received activity with THK id " thk-id
                                            (if teet-id
                                              (str "having TEET id " teet-id)
                                              "without TEET id => creating new activity"))]]
                      (merge
                       (cu/without-nils
                        (select-keys activity #{:thk.activity/id
                                                :activity/estimated-start-date
                                                :activity/estimated-end-date
                                                :activity/name
                                                :activity/status
                                                :activity/procurement-nr
                                                :activity/procurement-id}))
                       {:db/id (if teet-id
                                 [:integration/id teet-id]
                                 (str "act-" id))
                        :activity/integration-info (integration-info
                                                    activity
                                                    thk-mapping/activity-integration-info-fields)}))}))))}))]
     (task-updates rows))))

(defn teet-project? [[_ [p1 & _]]]
  (and p1
       (:thk.project/start-m p1)
       (not (excluded-project-types (:object/groupname p1)))))

(defn- thk-project-tx [db url projects-csv]
  (into [{:db/id "datomic.tx"
          :integration/source-uri url}]
        (mapcat
         (fn [prj]
           (when (teet-project? prj)
             (let [project-tx-maps (project-datomic-attributes db prj)
                   {:thk.project/keys [id lifecycles] :as project}
                   (first project-tx-maps)]
               (log/info "THK project " id
                         "has" (count lifecycles) "lifecycles (ids: "
                         (str/join ", " (map :thk.lifecycle/id lifecycles)) ") with"
                         (reduce + (map #(count (:thk.lifecycle/activities %)) lifecycles))
                         "activities.")
               project-tx-maps))))
        projects-csv))

(defn- check-unique-activity-ids [projects]
  (into {}
        (keep (fn [project]
                (let [activity-ids (map :thk.activity/id (-> project first second))
                      unique-activity-ids (into #{} activity-ids)]
                  (when (not= (count activity-ids) (count unique-activity-ids))
                    [;; project id as the key
                     (-> project first second first :thk.project/id)

                     ;; activity ids as the value
                     activity-ids]))))
        (partition 1 projects)))

(defn- check-unique-lifecycle-ids
  [projects]
  (into {}
        (keep (fn [[id lifecycles]]
                (let [lifecycle-ids (map :thk.lifecycle/id lifecycles)
                      unique-lifecycle-ids (into #{} lifecycle-ids)]
                  (when (not= (count lifecycle-ids) (count unique-lifecycle-ids))
                    [id
                     lifecycle-ids]))))
        projects))

(defn import-thk-projects! [connection url projects]
  (let [duplicate-activity-id-projects
        (check-unique-activity-ids projects)
        _duplicate-lifecycle-id-projects
        (check-unique-lifecycle-ids projects)]
    (when (seq duplicate-activity-id-projects)
      (throw (ex-info "Duplicate activity ids exist"
                      {:projects-with-duplicate-activity-ids duplicate-activity-id-projects})))
    ;; THK currently sends duplicate lifecycles
    #_(when (seq duplicate-lifecycle-id-projects)
      (throw (ex-info "Duplicate lifecycle ids exist"
                      {:projects-with-duplicate-lifecycle-ids duplicate-lifecycle-id-projects})))
    (let [db (d/db connection)]
      (d/transact connection
                  {:tx-data (thk-project-tx db url projects)}))))
