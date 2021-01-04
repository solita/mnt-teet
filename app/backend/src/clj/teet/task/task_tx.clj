(ns teet.task.task-tx
  "Transaction functions for tasks."
  (:require [teet.task.task-db :as task-db]
            [teet.meta.meta-model :as meta-model]
            [datomic.ion :as ion]))

(defn delete-task
  "Check all preconditions for deleting a task. If precondition
  fails, cancel the transactions. Otherwise return a tx that marks
  the task as deleted."
  [db user task-id]
  (cond
    (task-db/task-has-files? db task-id)
    (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                 :cognitect.anomalies/message "Task has files"
                 :teet/error :task-has-files})

    :else
    [(meta-model/deletion-tx user task-id)]))
