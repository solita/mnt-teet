(ns teet.comment.comment-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.document.document-storage :as document-storage]
            teet.document.document-spec
            [clojure.string :as str]
            [teet.meta.meta-model :refer [modification-meta creation-meta deletion-tx]]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

(defn comment-key-for-entity
  [db entity-id]
  (let [entity (d/pull db [:document/name :file/name] entity-id)]
    (cond
      (:document/name entity)
      :document/comments
      (:file/name entity)
      :file/comments
      :else
      (throw (ex-info "Can only comment on files and documents" {:comment-target-id entity-id})))))

(defmethod db-api/command! :comment/post-comment [{conn :conn
                                                   user :user}
                                                  {:keys [comment-target-id comment]}]
  (let [comment-key (comment-key-for-entity (d/db conn) comment-target-id)]
    (-> conn
        (d/transact {:tx-data [(merge {:db/id      comment-target-id
                                       comment-key [(merge {:db/id             "new-comment"
                                                            :comment/author    [:user/id (:user/id user)]
                                                            :comment/comment   comment
                                                            :comment/timestamp (java.util.Date.)}
                                                           (creation-meta user))]})]})
        (get-in [:tempids "new-comment"]))))

(defmethod db-api/command! :comment/delete-comment [{conn :conn
                                                     user :user}
                                                    {:keys [comment-id]}]
  (d/transact
    conn
    {:tx-data [(deletion-tx user comment-id)]})
  :ok)
