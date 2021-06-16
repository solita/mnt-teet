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
            [teet.project.project-db :as project-db]
            [teet.integration.integration-id :as integration-id]
            [teet.meta.meta-model :as meta-model])
  (:import (org.apache.commons.io.input BOMInputStream))
  (:import (java.util Date)))

(def excluded-project-types #{"TUGI"})

(def procurement-status-completed-fk "3202")

(defn parse-thk-export-csv [{:keys [input column-mapping group-by-fn]}]
  (with-open [raw-input-stream (io/input-stream input)
              input-stream (BOMInputStream. raw-input-stream)]
    (let [[headers & rows]
          (-> input-stream
              (io/reader :encoding "UTF-8")
              (csv/read-csv :separator \;))]
      ;; There are multiple rows per project. One for each project lifecycle phase.
      (dissoc
        (group-by group-by-fn
                  (into []
                        (comp
                          (map #(zipmap headers %))
                          (map #(into {}
                                      (for [[header value] %
                                            :let [{:keys [attribute parse]}
                                                  (column-mapping header)]
                                            :when (and attribute
                                                       (not (str/blank? value)))]

                                        [attribute ((or parse identity) value)]))))
                        rows))
        nil))))


(defn integration-info [row fields]
  (pr-str
   (into (sorted-map)
         (select-keys row fields))))

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

(defn- lookup
  "Lookup :db/id and :integration/id for an eid, returns nil if entity doesn't exist."
  [db eid]
  (let [result
        (ffirst
         (d/q '[:find (pull ?e [:db/id :integration/id])
                :in $ ?e]
              db eid))]
    (when (:db/id result)
      result)))

(defn project-tx-data [db [project-id rows]]
  (let [prj (first rows)
        phases (group-by :thk.lifecycle/id rows)
        phase-est-starts (keep :thk.lifecycle/estimated-start-date rows)
        phase-est-ends (keep :thk.lifecycle/estimated-end-date rows)

        project-est-start (first (sort phase-est-starts))
        project-est-end (last (sort phase-est-ends))

        ;; Lookup existing :db/id and :integration/id based on THK
        ;; project id
        {proj-db-id :db/id
         proj-integration-id :integration/id}
        (lookup db [:thk.project/id project-id])

        ;; Create new :integration/id if project does not have it
        proj-integration-id (or proj-integration-id
                                (let [new-uuid (integration-id/unused-random-small-uuid db)]
                                  (log/info "Creating new UUID for THK project "
                                            project-id " => " new-uuid)
                                  new-uuid))


        project-exists? (some? proj-db-id)
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

        (if project-exists?
          {:db/id proj-db-id
           :integration/id proj-integration-id}
          {:db/id (str "prj-" project-id)
           :integration/id proj-integration-id})

        {:thk.project/estimated-start-date project-est-start
         :thk.project/estimated-end-date project-est-end
         :thk.project/integration-info (integration-info
                                        prj thk-mapping/object-integration-info-fields)
         :thk.project/lifecycles
         (into []
               (for [[id activities] phases
                     :let [phase (first activities)
                           lc-thk-id (:thk.lifecycle/id phase)
                           lc-teet-id (:lifecycle-db-id phase)
                           {lc-db-id :db/id
                            lc-integration-id :integration/id}
                           (if lc-teet-id
                             ;; If THK sent TEET id field, lookup using that
                             (lookup db [:integration/id lc-teet-id])
                             ;; otherwise lookup using THK id
                             (lookup db [:thk.lifecycle/id lc-thk-id]))

                           _ (log/info "Received lifecycle with THK id " lc-thk-id
                                       (if lc-db-id
                                         (str "having TEET :db/id " lc-db-id)
                                         "without TEET id => creating new lifecycle")
                                       (when lc-teet-id
                                         (str "having TEET :integration/id " lc-teet-id)))
                           lc-integration-id (or lc-integration-id
                                                 (let [new-uuid (integration-id/unused-random-small-uuid db)]
                                                   (log/info "Creating new UUID for THK lifecycle " lc-thk-id " => " new-uuid)
                                                   new-uuid))]]
                 (cu/without-nils
                  (merge
                   (select-keys phase #{:thk.lifecycle/type
                                        :thk.lifecycle/estimated-start-date
                                        :thk.lifecycle/estimated-end-date
                                        :thk.lifecycle/id})
                   (if lc-db-id
                     ;; Existing lifecycle; will conflict if integration id and db id point to different entities
                     {:db/id lc-db-id
                      :integration/id lc-integration-id}

                     ;; New lifecycle
                     {:db/id (str "lfc-" id)
                      :integration/id lc-integration-id})

                   {:thk.lifecycle/integration-info (integration-info phase
                                                                      thk-mapping/phase-integration-info-fields)
                    :thk.lifecycle/activities
                    (for [{id :thk.activity/id
                           name :activity/name
                           task-id :activity/task-id
                           :as activity} activities
                          ;; Only process activities here except two specials with activity_typefk 4006 and 4009,
                          ;; which contain info about tasks created in THK and to be imported separately
                          :when (and
                                  id
                                  name
                                  (nil? task-id)
                                  (not (or
                                         (= name :activity.name/owners-supervision)
                                         (= name :activity.name/road-safety-audit))))
                          :let [{act-thk-id :thk.activity/id
                                 act-teet-id :activity-db-id} activity
                                {act-db-id :db/id
                                 act-integration-id :integration/id}
                                (if act-teet-id
                                  ;; Lookup using TEET id
                                  (lookup db [:integration/id act-teet-id])
                                  ;; Lookup using THK id
                                  (lookup db [:thk.activity/id act-thk-id]))
                                _ (log/info "Received activity with THK id " act-thk-id
                                            (if act-db-id
                                              (str "having TEET :db/id " act-db-id)
                                              "without TEET id => creating new activity")
                                            (when act-integration-id
                                              (str "having TEET :integration/id " act-integration-id)))
                                act-integration-id (or act-integration-id
                                                       (let [new-uuid (integration-id/unused-random-small-uuid db)]
                                                         (log/info "Creating new UUID for THK activity " act-thk-id " => " new-uuid)
                                                         new-uuid))]]
                      (merge
                       (cu/without-nils
                        (select-keys activity #{:thk.activity/id
                                                :activity/estimated-start-date
                                                :activity/estimated-end-date
                                                :activity/name
                                                :activity/status
                                                :activity/actual-end-date
                                                :activity/actual-start-date}))
                       (if act-db-id
                         ;; Existing activity
                         {:db/id act-db-id
                          :integration/id act-integration-id}

                         ;; New activity, create integration id
                         {:db/id (str "act-" id)
                          :integration/id act-integration-id})

                       {:activity/integration-info (integration-info
                                                    activity
                                                    thk-mapping/activity-integration-info-fields)}))}))))}))]
     (task-updates rows))))

(defn get-project-construction-activity-db-id
  [db rows]
  (let [construction-activity (first (filter (comp #{:activity.name/construction} :activity/name) rows))
        construction-activity-integration-id (:activity-db-id construction-activity)
        construction-activity-ids (lookup db [:integration/id construction-activity-integration-id])]
    (:db/id construction-activity-ids)))

(defn- get-project-attrs [db project-id rows]
  (let [phase-est-starts (keep :thk.lifecycle/estimated-start-date rows)
        phase-est-ends (keep :thk.lifecycle/estimated-end-date rows)
        thk-project (lookup db [:thk.project/id project-id])]
    {:prj (first rows)
     :phases (group-by :thk.lifecycle/id rows)

     :project-est-start (first (sort phase-est-starts))
     :project-est-end (last (sort phase-est-ends))

     :proj-integration-id (:integration/id thk-project)
     :proj-db-id (:db/id thk-project)

     :project-exists? (some? (:db/id thk-project))
     :project-has-owner? (and (some? (:db/id thk-project))
                           (project-db/project-has-owner? db [:thk.project/id project-id]))
     :construction-activity-db-id (get-project-construction-activity-db-id db rows)}))

(defn- get-existing-task-db-id
  "Search for the existing task with the same type and start and end dates"
  [db construction-activity-id task-type start-date end-date]
  (ffirst (d/q '[:find ?t
                 :where
                 [?a :activity/tasks ?t]
                 [(missing? $ ?t :meta/deleted?)]
                 [?t :task/estimated-end-date ?end-date]
                 [?t :task/estimated-start-date ?start-date]
                 [?t :task/type ?task-type]
                 :in $ ?a ?end-date ?start-date ?task-type]
            db construction-activity-id end-date start-date task-type)))

(defn- get-new-task-type [activity-data]
  (let [activity-name (:activity/name activity-data)]
    (case activity-name
      :activity.name/owners-supervision :task.type/owners-supervision
      :activity.name/road-safety-audit :task.type/road-safety-audit
      (throw (ex-info (str "new task type unknown for activity " activity-name)
               {:activity-data activity-data})))))

(defn- thk-task-group-supported-type [task-type]
 (case task-type
   :task.type/road-safety-audit :task.group/construction-approval
   :task.type/owners-supervision :task.group/construction-quality-assurance
   (throw (ex-info "THK Task type is not supported in import" {:task-type task-type}))))

(defn add-task-from-thk
  "Returns TX data for new task with given params for given construction activity-id"
  [db thk-activity-task-data construction-activity-id ]
  (let [start-date (:activity/estimated-start-date thk-activity-task-data)
        end-date (:activity/estimated-end-date thk-activity-task-data)
        actual-start-date (:activity/actual-start-date thk-activity-task-data)
        actual-end-date (:activity/actual-end-date thk-activity-task-data)
        thk-activity-status (:activity/status thk-activity-task-data)
        task-type (get-new-task-type thk-activity-task-data)
        existing-task-eid (get-existing-task-db-id db construction-activity-id task-type
                               start-date end-date)
        id-placeholder (if (some? existing-task-eid)
                         existing-task-eid
                         (str "NEW-TASK-" (name :task.group/construction) "-" task-type
                           "-" (integration-id/unused-random-small-uuid db)))
        task-group (thk-task-group-supported-type task-type)
        thk-activity-id (:thk.activity/id  thk-activity-task-data)
        task-tx-data (merge
                       {:db/id id-placeholder
                        :task/group task-group
                        :task/type task-type
                        :task/send-to-thk? true
                        :task/estimated-end-date end-date
                        :task/estimated-start-date start-date
                        :thk.activity/id thk-activity-id}
                       (when (some? actual-end-date)
                             {:task/actual-end-date actual-end-date})
                       (when (some? actual-start-date)
                             {:task/actual-start-date actual-start-date})
                       (when (nil? existing-task-eid)
                             {:integration/id (integration-id/unused-random-small-uuid db)
                              :meta/created-at (Date.)})
                       {:task/status (if (= :activity.status/completed thk-activity-status)
                                       :task.status/completed
                                       :task.status/not-started)})]
    task-tx-data))


(defn tasks-tx-data
  "If there is Construction activity then collect new tasks tx-s as [{task1 params} {task2 params} ...]
   for all others project's Activities of types: 4006 and 4009"
  [db [project-id rows]]
  (if-let [construction-activity-id (:construction-activity-db-id (get-project-attrs db project-id rows))]
    [{:db/id construction-activity-id
      :activity/tasks (reduce
                        #(conj %1 (add-task-from-thk db %2 construction-activity-id))
                        []
                        (filter #(or
                                   (= (:activity/name %) :activity.name/owners-supervision)
                                   (= (:activity/name %) :activity.name/road-safety-audit)) rows))}]))

(defn teet-project? [[_ [p1 & _]]]
  (and p1
       (not (excluded-project-types (:thk.project/repair-method p1)))))

(defn contract-exists?
  [db {:thk.contract/keys [procurement-id procurement-part-id] :as _contract-ids}]
  (if procurement-part-id
    (ffirst (d/q
              '[:find ?c
                :where
                [?c :thk.contract/procurement-id ?procurement-id]
                [?c :thk.contract/procurement-part-id ?procurement-part-id]
                :in $ ?procurement-id ?procurement-part-id]
              db procurement-id procurement-part-id))
    (ffirst (d/q
              '[:find ?c
                :where
                [?c :thk.contract/procurement-id ?procurement-id]
                [(missing? $ ?c :thk.contract/procurement-part-id)]
                :in $ ?procurement-id]
              db procurement-id))))

(defn contract-tx-data
  [db [contract-ids rows]]                                  ;; a contract can have multiple targets, each row has same contract information
  (let [{:thk.contract/keys [procurement-id procurement-part-id]
         :as contract-info}
        (first rows)]
    ;; check the contract-ids if they exist already
    (let [targets (->> rows
                    (mapv
                      (fn [target]
                        (if-let [target-id (or (:activity/task-id target) (:activity-db-id target))]
                          (:db/id (lookup db [:integration/id target-id]))
                          (:db/id (lookup db [:thk.activity/id (:thk.activity/id target)])))))
                    (filterv some?))
          regions (->> rows
                    (map :ta/region)
                    set)
          region-tx (when (= (count regions) 1)             ;; Only TX region when targets only have 1 region
                          {:ta/region (first regions)})
          contract-db-id (contract-exists? db contract-ids)]
      (if (not-empty targets)
        [(merge {:db/id (if-not contract-db-id
                          (str procurement-id "-" procurement-part-id "-new-contract")
                          contract-db-id)
                 :thk.contract/targets targets}
           region-tx
           (select-keys contract-info [:thk.contract/procurement-id
                                       :thk.contract/name
                                       :thk.contract/part-name
                                       :thk.contract/procurement-number
                                       :thk.contract/procurement-part-id
                                       :thk.contract/type])
           (when-not contract-db-id
                     (meta-model/system-created)))]
        (log/warn "No targets found for contract with ids: " contract-ids "Contract row details: " contract-info)))))

(defn final-contract?
  [[procurement-id [info & _]]]
  (and (some? procurement-id)
       (= (:thk.contract/procurement-status-fk info)
          procurement-status-completed-fk)))

(defn thk-import-contracts-tx
  [db url contract-rows]
  (into [{:db/id "datomic.tx-contracts"
          :integration/source-uri url}]
        (mapcat
          (fn [contract-row]
            (when (final-contract? contract-row)
              (let [contract-tx-map (contract-tx-data db contract-row)]
                contract-tx-map))))
        contract-rows))

(defn- thk-import-projects-tx [db url projects-csv]
  (into [{:db/id "datomic.tx"
          :integration/source-uri url}]
        (mapcat
         (fn [prj]                                          ;; {"project-id" [{"rows"}..]}
           (when (teet-project? prj)
             (let [project-tx-maps (project-tx-data db prj)
                   {:thk.project/keys [id lifecycles] :as _project}
                   (first project-tx-maps)]
               (log/info "THK project " id
                         "has" (count lifecycles) "lifecycles (ids: "
                         (str/join ", " (map :thk.lifecycle/id lifecycles)) ") with"
                         (reduce + (map #(count (:thk.lifecycle/activities %)) lifecycles))
                         "activities.")
               project-tx-maps))))
        projects-csv))

(defn- thk-import-tasks-tx [db url projects-csv]
  (let [tx-import-tasks (into [{:db/id "datomic.tx"
                :integration/source-uri url}]
          (mapcat
            (fn [prj]                                       ;; {"project-id" [{"rows"}..]}
              (when (teet-project? prj)
                    (let [tasks-tx-maps (tasks-tx-data db prj)]
                      tasks-tx-maps))))
                          projects-csv)]
    tx-import-tasks))

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

(defn import-thk-contracts! [connection url contracts]
  (let [db (d/db connection)]
    (d/transact connection
                {:tx-data (thk-import-contracts-tx db url contracts)})))

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
                  {:tx-data (thk-import-projects-tx db url projects)}))))

(defn import-thk-tasks! [connection url projects]
  (let [db (d/db connection)]
    (d/transact connection
      {:tx-data (thk-import-tasks-tx db url projects)})))
