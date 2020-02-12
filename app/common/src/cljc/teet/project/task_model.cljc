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

(def ^:const completed-statuses #{:task.status/completed :task.status/accepted})
(def ^:const in-progress-statuses #{:task.status/in-preparation :task.status/in-progress})

(defn completed? [{status :task/status}]
  (boolean (completed-statuses (:db/ident status))))

(defn rejected? [{status :task/status}]
  (-> status :db/ident (= :task.status/rejected)))

(defn in-progress? [{status :task/status}]
  (boolean (in-progress-statuses (:db/ident status))))
