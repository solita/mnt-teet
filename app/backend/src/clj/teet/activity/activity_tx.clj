(ns teet.activity.activity-tx
  "Transaction functions for activity"
  (:require
    [teet.activity.activity-db :as activity-db]
    [datomic.ion :as ion]
    [teet.meta.meta-model :as meta-model]))

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