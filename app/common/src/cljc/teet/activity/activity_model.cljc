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


(defn conflicting-schedules?
  "Returns true if there's a conflict in the schedules of the two
  activities. Land acquisition can coincide with another activity. If
  an activity is in a post-review state (completed, canceled,
  archived) it doesn't conflict. Actual dates are used if available,
  otherwise estimates are used."
  [a1 a2]
  (boolean
   (and
    ;; No conflict if at least one of the activities is post-review
    (not (or (reviewed-statuses (:activity/status a1))
             (reviewed-statuses (:activity/status a2))))
    ;; No conflict if one of the activities is land-acquisition
    (not (let [a1-name (:activity/name a1)
               a2-name (:activity/name a2)]
           (and (not= a1-name a2-name)
                (or (= a1-name :activity.name/land-acquisition)
                    (= a2-name :activity.name/land-acquisition)))))
    ;; Do the schedules overlap
    ;; Use actual date if exists, fallback to estimated
    (let [start1 (or (:activity/actual-start-date a1)
                     (:activity/estimated-start-date a1))
          end1 (or (:activity/actual-end-date a1)
                   (:activity/estimated-end-date a1))
          start2 (or (:activity/actual-start-date a2)
                     (:activity/estimated-start-date a2))
          end2 (or (:activity/actual-end-date a2)
                   (:activity/estimated-end-date a2))]
      (not (or (.before end1 start2)
               (.before end2 start1)))))))

(defn conflicts?
  "Are there conflicts preventing the two activities from coexisting
  within a lifecycle?"
  [a1 a2]
  (or (= (:activity/name a1) (:activity/name a2))
      (conflicting-schedules? a1 a2)))
