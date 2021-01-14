(ns teet.activity.activity-tx
  "Transaction functions for activity"
  (:require
    [teet.activity.activity-db :as activity-db]
    [datomic.ion :as ion]
    [teet.meta.meta-model :as meta-model]
    [teet.activity.activity-model :as activity-model]
    [datomic.client.api :as d]))

(defn delete-activity
  "Check all preconditions for deleting an activity.
  If precondition fails, cancel the transactions.
  Otherwise return a tx that marks the activity as deleted."
  [db user activity-id]
  (cond
    (activity-db/activity-task-has-files? db activity-id)
    (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                 :cognitect.anomalies/message "Activity Task has files"
                 :teet/error :activity-task-has-files})

    :else
    (into [(meta-model/deletion-tx user activity-id)]
      (map (partial meta-model/deletion-tx user))
      (activity-db/activity-tasks db activity-id))))

(defn ensure-no-overlapping-activities
  [db lifecycle-id]
  (let [activities (activity-db/current-lifecycle-activities db lifecycle-id)]
    (doseq [a1 activities
            a2 activities]
      (when (and (not= (:db/id a1) (:db/id a2))
                 (activity-model/conflicts? a1 a2))
        (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                     :cognitect.anomalies/message "Conflicting activities"
                     :teet/error :conflicting-activities})))))

(defn ensure-no-duplicate-active-tasks
  [db activity-id]
  (let [tasks (activity-db/activitys-active-tasks db activity-id)
        task-types (mapv #(get-in % [:task/type :db/ident]) tasks)]
    (when (not= (count task-types)
                (count (distinct task-types)))
      (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                   :cognitect.anomalies/message "conflicting tasks"
                   :teet/error :conflicting-tasks}))))

(defn ensure-activity-validity
  "Check that the to be created activity is in no conflict with existing data"
  [db lifecycle-id tx-data]
  (let [{db-after :db-after} (d/with db {:tx-data tx-data})
        activity-ids (activity-db/get-activity-ids-of-lifecycle db-after lifecycle-id)]
    (ensure-no-overlapping-activities db-after lifecycle-id)
    (doseq [activity-id activity-ids]
      (ensure-no-duplicate-active-tasks db-after activity-id))
    tx-data))
