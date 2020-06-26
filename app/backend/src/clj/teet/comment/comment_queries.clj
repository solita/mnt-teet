(ns teet.comment.comment-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.authorization.authorization-check :as ac]
            [teet.comment.comment-db :as comment-db]
            [teet.project.project-db :as project-db]))

(defn- comment-visibility [user project-id]
  (when-not
      (ac/authorized? user
                      :project/view-internal-comments
                      {:project-id project-id})
    :comment.visibility/all))

(defquery :comment/fetch-comments
  {:doc "Fetch comments for any :db/id and entity type. Returns comments newest first."
   :context {user :user db :db}
   :args {id :db/id entity-type :for}
   :project-id (project-db/entity-project-id db entity-type id)
   :authorization {:project/read-comments {:db/id id}}}
  (->> (comment-db/comments-of-entity db id entity-type
                                      (comment-visibility
                                       user
                                       (project-db/entity-project-id db entity-type id)))
       (sort-by :comment/timestamp)
       reverse
       vec))

(defquery :comment/count
  {:doc "Fetch the amount of comments for entity."
   :context {user :user db :db}
   :args {id :db/id entity-type :for}
   :project-id (project-db/entity-project-id db entity-type id)
   :authorization {:project/read-comments {:db/id id}}}
  (comment-db/comment-count-of-entity db id entity-type
                                      (comment-visibility
                                       user
                                       (project-db/entity-project-id db entity-type id))))
