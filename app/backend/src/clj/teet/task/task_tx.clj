(ns teet.task.task-tx
  "Transaction functions for tasks."
  (:require [datomic.client.api :as d]
            [teet.task.task-db :as task-db]
            [teet.meta.meta-model :as meta-model]
            [datomic.ion :as ion]))

(defn delete-task
  "Check all preconditions for deleting a task. If precondition
  fails, cancel the transactions. Otherwise return a tx that marks
  the task as deleted."
  [db user task-id]
  (cond
    (task-db/send-to-thk? db task-id)
    (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/incorrect
                 :cognitect.anomalies/message "Can't delete task sent to THK"
                 :teet/error :task-is-sent-to-thk})

    :else
    [(meta-model/deletion-tx user task-id)]))
