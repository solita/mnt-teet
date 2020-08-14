(ns teet.comment.comment-db
  "Utilities for comment datomic queries"
  (:require [datomic.client.api :as d]
            [teet.meta.meta-query :as meta-query]
            [teet.db-api.core :as db-api]
            [teet.comment.comment-model :as comment-model]
            [teet.util.datomic :as du]
            [teet.user.user-model :as user-model]
            [teet.authorization.authorization-check :as ac]
            [teet.util.collection :as cu]
            [teet.project.project-db :as project-db]))


(defn- comment-query [type visibility pull-selector]
  {:pre [(comment-model/type->comments-attribute type)]}
  {:find [(list 'pull
                '?comment
                pull-selector)]
   :in '[$ ?entity-id]
   :where (into [['?entity-id
                  (comment-model/type->comments-attribute type)
                  '?comment]]
                (concat
                 ;; Comment is not deleted
                 '[[(missing? $ ?comment :meta/deleted?)]]

                 (when visibility
                   [['?comment :comment/visibility visibility]])))})

(defn- resolve-id [db entity-id]
   (if (number? entity-id)
     entity-id
     (:db/id (du/entity db entity-id))))

(defn comments-of-entity
  ([db entity-id entity-type]
   (comments-of-entity db entity-id entity-type nil))
  ([db entity-id entity-type comment-visibility]
   (comments-of-entity db entity-id entity-type comment-visibility
                       '[*
                         {:comment/author [:db/id :user/id :user/given-name :user/family-name :user/email :user/person-id]
                          :comment/mentions [:user/given-name :user/family-name :user/id :user/person-id :user/email]
                          :comment/files [:db/id :file/name]}]))
  ([db entity-id entity-type comment-visibility pull-selector]
   (if-let [resolved-id (resolve-id db entity-id)]
     (->> (d/q (comment-query entity-type
                              comment-visibility
                              pull-selector)
               db resolved-id)
          (map first)
          (meta-query/without-deleted db))
     [])))

(defn comment-visibility [user project-id]
  (when-not
    (ac/authorized? user
                    :project/view-internal-comments
                    {:project-id project-id})
    :comment.visibility/all))

(defn entity-comments-last-seen-by-user
  [db user entity-id]
  (when-let [seen-at (:comments-seen/seen-at
                       (d/pull db '[:comments-seen/seen-at]
                               [:comments-seen/entity+user [entity-id user]]))]
    seen-at))

(defn comment-count-of-entity-by-status
  [db user entity-id entity-type]
  (if-let [resolved-id (resolve-id db entity-id)]
    (let [project-id (project-db/entity-project-id db entity-type entity-id)
          resolved-user (resolve-id db (user-model/user-ref user))
          visibility (comment-visibility user project-id)
          comments-last-seen (entity-comments-last-seen-by-user db resolved-user resolved-id)
          comments (comments-of-entity db resolved-id entity-type visibility '[:meta/created-at :meta/creator])
          grouped-comments (merge
                             {:comment/old-comments 0
                              :comment/new-comments 0}
                             (cu/count-by
                               (fn [comment]
                                 (if (or
                                       (and comments-last-seen (.before (:meta/created-at comment) comments-last-seen))
                                       (= (get-in comment [:meta/creator :db/id]) resolved-user))
                                   :comment/old-comments
                                   :comment/new-comments))
                               comments))]
      {:comment/counts grouped-comments})
    {:comment/counts {:comment/old-comments 0
                      :comment/new-comments 0}}))

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
        comment-model/type->comments-attribute))
