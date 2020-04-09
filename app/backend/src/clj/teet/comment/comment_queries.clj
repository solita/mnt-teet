(ns teet.comment.comment-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.project.project-db :as project-db]
            [teet.comment.comment-model :as comment-model]))



(defquery :comment/fetch-comments
  {:doc "Fetch comments for any :db/id and entity type. Returns comments newest first."
   :context {db :db}
   :args {id :db/id entity-type :for}
   :project-id (project-db/entity-project-id db entity-type id)
   :authorization {}}
  (let [attr (comment-model/comments-attribute-for-entity-type entity-type)
        entity-comments (d/pull db
                                [{attr '[*
                                         {:comment/author [*]}
                                         {:comment/files [:db/id :file/name]}]}] id)]
    (if (empty? entity-comments)
      []
      (->> entity-comments

           attr
           (sort-by :comment/timestamp)
           reverse
           vec))))
