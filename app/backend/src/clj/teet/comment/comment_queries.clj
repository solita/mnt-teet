(ns teet.comment.comment-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.project.project-db :as project-db]))

(defquery :comment/fetch-comments
  {:doc "Fetch comments for any :db/id"
   :context {db :db}
   :args {id :db/id}
   :project-id (project-db/task-project-id db id)
   :authorization {}}
  (let [task-comments (d/pull db
                              ;; Determine what comments attribute to use instead of :task/comments
                              '[{:task/comments [* {:comment/author [*]}]}] id)]
    (if (empty? task-comments)
      []
      (->> task-comments
           :task/comments
           (sort-by :comment/timestamp)
           reverse
           vec))))
