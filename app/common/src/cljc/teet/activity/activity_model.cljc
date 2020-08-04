(ns teet.activity.activity-model
  (:require [teet.project.task-model :as task-model]
            [teet.util.datomic :as du]))

(defn all-tasks-completed? [activity]
  "Expects the meta/deleted? key in tasks if they are deleted"
  (let [tasks (filter
                (fn [task]
                  (not (:meta/deleted? task)))
                (:activity/tasks activity))]
    (and (not-empty tasks)
         (every? task-model/completed?
                 tasks))))

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

(def activity-finished-statuses
  #{:activity.status/completed :activity.status/expired :activity.status/canceled})

(defn deletable?
  "Can the activity be deleted? It can if it has no procurement number."
  [activity]
  (nil? (:activity/procurement-nr activity)))

(defn active? [activity]
  (-> activity :activity/status :db/ident activity-in-progress-statuses))

(defn finished? [activity]
  (-> activity :activity/status :db/ident activity-finished-statuses))

(defn conflicting-schedules?
  "Returns true if there's a conflict in the schedules of the two
  activities. Land acquisition can coincide with another
  activity. Actual dates are used if available, otherwise estimates
  are used."
  [a1 a2]
  (boolean
   (and
    ;; No conflict if one of the activities is land-acquisition
    (not (let [a1-name (:activity/name a1)
               a2-name (:activity/name a2)]
           (and (not= a1-name a2-name)
                (or (du/enum= a1-name :activity.name/land-acquisition)
                    (du/enum= a2-name :activity.name/land-acquisition)))))
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
  within a lifecycle?  If an activity is in a post-review
  state (completed, canceled, archived) it doesn't conflict."
  [a1 a2]
  ;; If either of the activities is completed/archived/canceled,
  ;; there are no conflicts
  (when (not (or (reviewed-statuses (:activity/status a1))
                 (reviewed-statuses (:activity/status a2))))
   (or
    ;; Two incomplete activies of same type (name) cannot coexist within a lifecycle
    (= (:activity/name a1) (:activity/name a2))

    (conflicting-schedules? a1 a2))))
