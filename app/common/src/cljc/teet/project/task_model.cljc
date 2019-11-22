(ns teet.project.task-model
  (:require [teet.util.datomic :refer [id=]]))

(defn document-by-id [{documents :task/documents} document-id]
  (some #(when (id= document-id (:db/id %)) %) documents))
