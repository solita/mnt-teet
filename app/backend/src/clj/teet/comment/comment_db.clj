(ns teet.comment.comment-db
  "Utilities for comment datomic queries"
  (:require [datomic.client.api :as d]
            [teet.meta.meta-query :as meta-query]
            [teet.db-api.core :as db-api]))

(def ^:private type->comments-attribute
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
                          :comment/files [:db/id :file/name]}]))
  ([db entity-id entity-type comment-visibility pull-selector]
   (->> (d/q (comment-query entity-type
                            comment-visibility
                            pull-selector)
             db entity-id)
        (map first)
        (meta-query/without-deleted db))))
