(ns teet.project.task-model
  (:require [teet.util.datomic :refer [id=]]
            [teet.util.collection :refer [find-idx]]))

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


(defn completed? [{status :task/status}]
  (boolean (completed-statuses (:db/ident status))))

(defn rejected? [{status :task/status}]
  (boolean (rejected-statuses (:db/ident status))))

(defn in-progress? [{status :task/status}]
  (boolean (in-progress-statuses (:db/ident status))))
