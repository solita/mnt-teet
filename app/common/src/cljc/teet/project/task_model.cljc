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

(def ^:const completed-statuses #{:task.status/accepted
                                  ;; unused/obsolete statuses:
                                  :task.status/completed})
(def ^:const in-progress-statuses #{:task.status/in-preparation
                                    :task.status/adjustment
                                    :task.status/reviewing
                                    ;; unused/obsolete statuses:
                                    :task.status/in-progress})
(def ^:const rejected-statuses #{:task.status/canceled
                                 ;; unused/obsolete statuses:
                                 :task.status/rejected})

;; statuses excluding obsoleted ones, that should be selectable in status changes
(def ^:const current-statuses #{:task.status/canceled
                                :task.status/assigned
                                :task.status/submitted
                                :task.status/reviewing
                                :task.status/adjustment
                                :task.status/accepted})


(def ^:const waiting-for-review-statuses #{:task.status/waiting-for-review})

(def ^:const accepted-statuses #{:task.status/accepted})
(def ^:const review-outcome-statuses #{:task.status/accepted :task.status/rejected})

(defn- in-status [statuses task]
  (boolean (statuses (get-in task [:task/status :db/ident]))))

(def waiting-for-review? (partial in-status waiting-for-review-statuses))
(def completed? (partial in-status completed-statuses))
(def rejected? (partial in-status rejected-statuses))
(def accepted? (partial in-status accepted-statuses))
(def in-progress? (partial in-status in-progress-statuses))

(defn can-submit?
  "Determine if the task results can be submitted. If true, task
  result files can be added and the task can be sent for review."
  [task]
  (and (not (waiting-for-review? task))
       (not (accepted? task))))



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
