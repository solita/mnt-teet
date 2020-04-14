(ns teet.comment.comment-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.authorization.authorization-check :as ac]
            [teet.meta.meta-query :as meta-query]
            [teet.project.project-db :as project-db]))

(def type->comments-attribute
  {:task :task/comments
   :file :file/comments})

(defn comment-query [type visibility]
  {:pre [(type->comments-attribute type)]}
  {:find '[(pull ?comment [*
                           {:comment/author [*]
                            :comment/files [:db/id :file/name]}])]
   :in '[$ ?entity-id]
   :where (into [['?entity-id
                  (type->comments-attribute type)
                  '?comment]]
                (when visibility
                  [['?comment :comment/visibility visibility]]))})

(defquery :comment/fetch-comments
  {:doc "Fetch comments for any :db/id and entity type. Returns comments newest first."
   :context {user :user db :db}
   :args {id :db/id entity-type :for}
   :project-id (project-db/entity-project-id db entity-type id)
   :authorization {:land/read-comments {:db/id id}}}
  (let [visibility (when-not (ac/authorized? user
                                             :project/view-internal-comments)
                     :comment.visibility/all)
        entity-comments (->> (d/q (comment-query entity-type visibility)
                                  db id)
                             (map first))]
    (if (empty? entity-comments)
      []
      (->> entity-comments
           (meta-query/without-deleted db)
           (sort-by :comment/timestamp)
           reverse
           vec))))
