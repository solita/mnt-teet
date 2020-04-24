(ns teet.project.task-model
  (:require [teet.util.datomic :refer [id=]]
            [teet.util.collection :refer [find-idx]]
            [teet.util.date :as date]))

(def task-group-order
  {:task.group/base-data 1
   :task.group/study 2
   :task.group/land-purchase 3
   :task.group/design 4
   :task.group/design-approval 4})

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

(defn- in-status [status task]
  (boolean (= status (get-in task [:task/status :db/ident]))))

(def reviewing? (partial in-status :task.status/reviewing))
(def waiting-for-review? (partial in-status :task.status/waiting-for-review))
(def completed? (partial in-status :task.status/completed))

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
                (and (nil? assignee) (date/date-in-past? estimated-start-date))
                :unassigned-past-start-date
                (and (date/date-in-past? estimated-end-date) (not (completed? task)))
                :task-over-deadline
                (and (> 7 (date/days-until-date estimated-end-date)) (not (completed? task)))
                :close-to-deadline
                (and (> (date/days-until-date estimated-end-date) 7) (some? assignee))
                :in-progress
                :else
                :unassigned)))
