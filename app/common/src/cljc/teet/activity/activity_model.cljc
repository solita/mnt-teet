(ns teet.activity.activity-model
  (:require [teet.project.task-model :as task-model]))

(defn all-tasks-completed? [activity]
  (every? task-model/completed?
          (:activity/tasks activity)))

(def reviewed-statuses #{:activity.status/canceled
                         :activity.status/archived
                         :activity.status/completed})
