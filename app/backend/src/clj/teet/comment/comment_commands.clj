(ns teet.comment.comment-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            teet.file.file-spec
            [teet.meta.meta-model :refer [creation-meta modification-meta deletion-tx]]
            [teet.comment.comment-model :as comment-model]
            [teet.project.project-db :as project-db])
  (:import (java.util Date)))

(defn- validate-files
  "Validate comment attachments. They must be created
  by the same user and they must be images."
  [db user files]
  (let [user-created-files
        (into #{}
              (map first)
              (d/q '[:find ?c
                     :where
                     [?c :meta/creator ?user]
                     [?c :file/type ?type]
                     [(.startsWith ^String ?type "image/")]
                     :in $ $user [?c ...]]
                   db
                   [:user/id (:user/id user)]
                   files))]
    (if-not (every? user-created-files files)
      (db-api/bad-request! "No such file")
      files)))

(defcommand :comment/create
  {:doc "Create a new comment and add it to an entity"
   :context {:keys [db user]}
   :payload {:keys [entity-id entity-type comment files visibility]}
   :project-id (project-db/entity-project-id db entity-type entity-id)
   :authorization {:task/comment-task {:db/id entity-id}}
   :transact [(merge {:db/id entity-id
                      (comment-model/comments-attribute-for-entity-type entity-type)
                      [(merge {:db/id "new-comment"
                               :comment/author [:user/id (:user/id user)]
                               :comment/comment comment
                               :comment/visibility visibility
                               :comment/timestamp (Date.)}
                              (creation-meta user)
                              (when (seq files)
                                {:comment/files (validate-files db user files)}))]})]})

(defn- comment-parent-entity [db comment-id]
  (if-let [doc-id (ffirst
                   (d/q '[:find ?doc
                          :in $ ?comment
                          :where [?doc :document/comments ?comment]]
                        db comment-id))]
    [:document doc-id]
    (if-let [file-id (ffirst
                      (d/q '[:find ?file
                             :in $ ?comment
                             :where [?file :file/comments ?comment]]
                           db comment-id))]
      [:file file-id]
      nil)))

(defcommand :comment/update
  {:doc "Update existing comment"
   :context {:keys [db user]}
   :payload {:keys [comment-id comment]}
   :project-id (let [[parent-type parent-id] (comment-parent-entity db comment-id)]
                 (case parent-type
                   :document (project-db/document-project-id db parent-id)
                   :file (project-db/file-project-id db parent-id)
                   (db-api/bad-request! "No such comment")))
   :authorization {:document/delete-comment {:db/id comment-id}} ;; TODO: Do we have comment edit permission?
   :transact [(merge {:db/id comment-id
                      :comment/comment comment}
                     (modification-meta user))]})

(defcommand :comment/delete-comment
  {:doc "Delete existing comment"
   :context {:keys [db user]}
   :payload {:keys [comment-id]}
   :project-id (let [[parent-type parent-id] (comment-parent-entity db comment-id)]
                 (case parent-type
                   :document (project-db/document-project-id db parent-id)
                   :file (project-db/file-project-id db parent-id)
                   (db-api/bad-request! "No such comment")))
   :authorization {:document/delete-comment {:db/id comment-id}}
   :transact [(deletion-tx user comment-id)]})
