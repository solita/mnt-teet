(ns teet.comment.comment-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.project.project-db :as project-db]))

(defquery :task/fetch-comments
  {:doc "Fetch comments for any :db/id"
   :context {db :db}
   :args {id :db/id}
   :project-id (project-db/task-project-id db id)
   :authorization {}}
  (let [task-comments (d/pull db
                              '[{:task/comments [* {:comment/author [*]}]}] id)]
    (:task/comments task-comments)))

