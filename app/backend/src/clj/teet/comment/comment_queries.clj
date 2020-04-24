(ns teet.comment.comment-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.authorization.authorization-check :as ac]
            [teet.comment.comment-db :as comment-db]
            [teet.project.project-db :as project-db]))

(defquery :comment/fetch-comments
  {:doc "Fetch comments for any :db/id and entity type. Returns comments newest first."
   :context {user :user db :db}
   :args {id :db/id entity-type :for}
   :project-id (project-db/entity-project-id db entity-type id)
   :authorization {:project/read-comments {:db/id id}}}
  (let [comment-visibility (when-not (ac/authorized? user
                                                     :project/view-internal-comments)
                             :comment.visibility/all)]
    (->> (comment-db/comments-of-entity db id entity-type
                                        comment-visibility)
         (sort-by :comment/timestamp)
         reverse
         vec)))
