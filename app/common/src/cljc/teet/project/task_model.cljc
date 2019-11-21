(ns teet.project.task-model
  [:require [teet.project.project-model :as project-model]])

(defn document-by-id [{documents :task/documents} document-id]
  (some #(when (project-model/id= document-id (:db/id %)) %) documents))
