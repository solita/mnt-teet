(ns teet.comment.comment-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.authorization.authorization-check :as ac]
            [teet.meta.meta-query :as meta-query]
            [teet.project.project-db :as project-db]
            [teet.comment.comment-model :as comment-model]))

(defn- filter-by-visibility [can-see-internal-comments? comments]

  (cond->> comments
    (not can-see-internal-comments?) (filter #(= (-> % :comment/visibility :db/ident)
                                                 :comment.visibility/all))))

(defquery :comment/fetch-comments
  {:doc "Fetch comments for any :db/id and entity type. Returns comments newest first."
   :context {user :user db :db}
   :args {id :db/id entity-type :for}
   :project-id (project-db/entity-project-id db entity-type id)
   :authorization {:land/read-comments {:db/id id}}}
  (let [entity-comments (->> (if (= entity-type :task)
                               (d/q '[:find (pull ?comment [*
                                                            {:comment/author [*]
                                                             :comment/files [:db/id :file/name]}])
                                      :in $ ?entity-id
                                      :where [?entity-id :task/comments ?comment]]
                                    db id)
                               (d/q '[:find (pull ?comment [*
                                                            {:comment/author [*]
                                                             :comment/files [:db/id :file/name]}])
                                      :in $ ?entity-id
                                      :where [?entity-id :file/comments ?comment]]
                                    db id))
                             (map first)
                             (remove nil?))]
    (if (empty? entity-comments)
      []
      (->> entity-comments
           (meta-query/without-deleted db)
           (filter-by-visibility (ac/authorized? user
                                                 :project/view-internal-comments))
           (sort-by :comment/timestamp)
           reverse
           vec))))
