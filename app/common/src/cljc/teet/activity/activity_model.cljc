(ns teet.activity.activity-model
  (:require [teet.project.task-model :as task-model]))

(defn all-tasks-completed? [activity]
  (let [statuses (->> activity :activity/tasks (mapv (comp :db/ident :task/status)))
        all-complete? (every? task-model/completed-statuses statuses)]
    all-complete?))
