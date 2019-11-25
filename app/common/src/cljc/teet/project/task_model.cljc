(ns teet.project.task-model
  (:require [teet.util.datomic :refer [id=]]))

(defn file-by-id [{documents :task/documents} file-id]
  (some
    (fn [{files :document/files}]
      (some #(when (id= file-id (:db/id %)) %) files))
    documents))

(defn document-by-id [{documents :task/documents} document-id]
  (some #(when (id= document-id (:db/id %)) %) documents))
