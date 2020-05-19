(ns teet.comment.comment-db
  "Utilities for comment datomic queries"
  (:require [datomic.client.api :as d]
            [teet.meta.meta-query :as meta-query]
            [teet.db-api.core :as db-api]
            [teet.comment.comment-model :as comment-model]))

(def type->comments-attribute
  {:task :task/comments
   :file :file/comments})

(defn- comment-query [type visibility pull-selector]
  {:pre [(type->comments-attribute type)]}
  {:find [(list 'pull
                '?comment
                pull-selector)]
   :in '[$ ?entity-id]
   :where (into [['?entity-id
                  (type->comments-attribute type)
                  '?comment]]
                (when visibility
                  [['?comment :comment/visibility visibility]]))})

(defn comments-of-entity
  ([db entity-id entity-type]
   (comments-of-entity db entity-id entity-type nil))
  ([db entity-id entity-type comment-visibility]
   (comments-of-entity db entity-id entity-type comment-visibility
                       '[*
                         {:comment/author [*]
                          :comment/mentions [:user/given-name :user/family-name]
                          :comment/files [:db/id :file/name]}]))
  ([db entity-id entity-type comment-visibility pull-selector]
   (->> (d/q (comment-query entity-type
                            comment-visibility
                            pull-selector)
             db entity-id)
        (map first)
        (meta-query/without-deleted db))))

(defn comment-parent
  "Returns [entity-type entity-id] for the parent of the given comment.
  If parent is not found, returns nil."
  [db comment-id]
  (some (fn [[entity-type attr]]
          (when-let [entity-id (ffirst
                                (d/q [:find '?e
                                      :where ['?e attr '?c]
                                      :in '$ '?c]
                                     db comment-id))]
            [entity-type entity-id]))
        comment-model/entity-comment-attribute))
