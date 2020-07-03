(ns teet.activity.activity-model
  (:require [teet.project.task-model :as task-model]))

(defn all-tasks-completed? [activity]
  (and (not-empty (:activity/tasks activity))
       (every? task-model/completed?
                (:activity/tasks activity))))

(def reviewed-statuses #{:activity.status/canceled
                         :activity.status/archived
                         :activity.status/completed})

(def activity-order
  {:activity.name/pre-design 1
   :activity.name/preliminary-design 2
   :activity.name/detailed-design 3
   :activity.name/land-acquisition 4
   :activity.name/workshop-design 5
   :activity.name/construction 6})

(def activity-name->task-groups
  {:activity.name/pre-design #{:task.group/base-data
                               :task.group/study
                               :task.group/design
                               :task.group/design-approval}
   :activity.name/preliminary-design #{:task.group/base-data
                                       :task.group/study
                                       :task.group/design
                                       :task.group/design-approval
                                       :task.group/design-reports}
   :activity.name/detailed-design #{:task.group/base-data
                                    :task.group/study
                                    :task.group/design
                                    :task.group/design-approval
                                    :task.group/design-reports}
   :activity.name/land-acquisition #{:task.group/land-purchase}
   ;; TODO: No known task groups for these yet
   :activity.name/construction #{}
   :activity.name/workshop-design #{}})

(def activity-in-progress-statuses
  #{:activity.status/valid :activity.status/other :activity.status/research :activity.status/in-progress})

(def activity-ready-statuses
  #{:activity.status/completed :activity.status/expired :activity.status/canceled})

(defn deletable?
  "Can the activity be deleted? It can if it has no procurement number."
  [activity]
  (nil? (:activity/procurement-nr activity)))

(defn currently-active?
  "Is the activity currently active? An activity is active if the
  current timestamp is between the actual start and end date of the
  activity."
  [{:activity/keys [actual-start-date actual-end-date]} current-timestamp]
  {:pre [(instance? java.util.Date current-timestamp)]}
  ;; must have started to be active
  (boolean (and actual-start-date
                (not (.before current-timestamp actual-start-date))
                (or (not actual-end-date)
                    (not (.after current-timestamp actual-end-date))))))
