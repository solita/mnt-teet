(ns teet.project.task-model
  (:require [teet.util.datomic :refer [id=] :as du]
            [teet.util.collection :refer [find-idx]]
            [teet.util.date :as date]
            [taoensso.timbre :as log]))

(def edit-form-keys
  #{:db/id
    :task/description
    :task/estimated-start-date
    :task/estimated-end-date
    :task/actual-start-date
    :task/actual-end-date
    :task/assignee})

(def not-creatable-in-teet
  #{:task.type/owners-supervision :task.type/road-safety-audit})

(def task-group-order
  {:task.group/base-data 1
   :task.group/study 2
   :task.group/design 3
   :task.group/design-approval 4
   :task.group/design-reports 5
   :task.group/land-purchase 6
   :task.group/working-design 7
   :task.group/construction 8
   :task.group/construction-quality-assurance 9
   :task.group/construction-approval 10
   :task.group/warranty 11})

(defn file-by-id-path
  "Returns vector path to the given file in task."
  [task file-id]
  (first
   (keep-indexed
    (fn [doc-idx doc]
      (when-let [file-idx (find-idx #(id= (:db/id %) file-id) (:document/files doc))]
        [:task/documents doc-idx :document/files file-idx]))
    (:task/documents task))))

(defn file-by-id [task file-id]
  (when-let [file-path (file-by-id-path task file-id)]
    (get-in task file-path)))

(defn document-by-id [{documents :task/documents} document-id]
  (some #(when (id= document-id (:db/id %)) %) documents))

(defn- in-status [status {task-status :task/status}]
  (du/enum= status task-status))

(def reviewing? (partial in-status :task.status/reviewing))
(def waiting-for-review? (partial in-status :task.status/waiting-for-review))
(def completed? (partial in-status :task.status/completed))

(defn- part-in-status [status {part-status :file.part/status}]
  (du/enum= status part-status))

(def part-reviewing? (partial part-in-status :file.part.status/reviewing))
(def part-waiting-for-review? (partial part-in-status :file.part.status/waiting-for-review))
(def part-completed? (partial part-in-status :file.part.status/completed))

(defn can-submit?
  "Determine if the task results can be submitted. If true, task
  result files can be added and the task can be sent for review."
  [task]
  (and (not (waiting-for-review? task))
       (not (reviewing? task))
       (not (completed? task))))

(defn task-with-status
  [{:task/keys [assignee estimated-start-date estimated-end-date] :as task}]
  (assoc task :task/derived-status
              (cond
                (completed? task)
                :done
                (or (nil? estimated-end-date) (nil? estimated-start-date))
                :unknown-status
                (and (nil? assignee) (date/date-before-today? estimated-start-date))
                :unassigned-past-start-date
                (and (date/date-before-today? estimated-end-date) (not (completed? task)))
                :task-over-deadline
                (and (> 7 (date/days-until-date estimated-end-date)) (not (completed? task)))
                :close-to-deadline
                (and (> (date/days-until-date estimated-end-date) 7) (some? assignee))
                :in-progress
                :else
                :unassigned)))

(defn any-task-part-waiting-for-review?
  "Checks if any task parts under the task are in waiting for review status"
  [task-parts]
      (some (fn [x]
                      (= :file.part.status/waiting-for-review
                         (:db/ident (:file.part/status x))))
                    (:file.part/_task task-parts)))

(defn task-assignee
  "Fetch task assignee based on task-id.
  Goes through lifecycles and activities and returns a task assignee for matching id."
  [{lcs :thk.project/lifecycles} task-id]
  (some
    (fn [{activities :thk.lifecycle/activities}]
      (some (fn [{tasks :activity/tasks}]
              (some #(when (id= (:db/id %) task-id) (:db/id (:task/assignee %))) tasks))
            activities))
    lcs))

(defn can-submit-part?
  "Check if the task part can be submitted."
  [task-part]
  (and (not (part-waiting-for-review? task-part))
       (not (part-reviewing? task-part))
       (not (part-completed? task-part))))
