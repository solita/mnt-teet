(ns teet.comment.comment-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            teet.file.file-spec
            [teet.meta.meta-model :refer [creation-meta deletion-tx]]
            [teet.project.project-db :as project-db])
  (:import (java.util Date)))

(defcommand :comment/comment-on-document
  {:doc "Add new comment on a document entity"
   :context {:keys [db user]}
   :payload {:keys [document-id comment]}
   :project-id (project-db/document-project-id db document-id)
   :authorization {:document/comment-on-document {:db/id document-id}}
   :transact [(merge {:db/id document-id
                      :document/comments [(merge {:db/id             "new-comment"
                                                  :comment/author    [:user/id (:user/id user)]
                                                  :comment/comment   comment
                                                  :comment/timestamp (Date.)}
                                                 (creation-meta user))]})]})

(defcommand :comment/comment-on-task
  {:doc "Add a new comment on a task entity"
   :context {:keys [db user]}
   :payload {:keys [entity-id comment]}
   :project-id (project-db/task-project-id db entity-id)
   :authorization {:task/comment-task {:db/id entity-id}}
   :transact [(merge {:db/id entity-id
                      :task/comments [(merge {:db/id "new-comment"
                                              :comment/author [:user/id (:user/id user)]
                                              :comment/comment comment
                                              :comment/timestamp (Date.)}
                                             (creation-meta user))]})]})

(defcommand :comment/comment-on-file
  {:doc "Add new comment on a document entity"
   :context {:keys [db user]}
   :payload {:keys [file-id comment]}
   :project-id (project-db/file-project-id db file-id)
   :authorization {:document/comment-on-document
                   {:db/id (ffirst
                            (d/q '[:find ?doc
                                   :in $ ?file
                                   :where
                                   [?doc :document/files ?file]]
                                 db file-id))}}
   :transact [(merge {:db/id file-id
                      :file/comments [(merge {:db/id             "new-comment"
                                              :comment/author    [:user/id (:user/id user)]
                                              :comment/comment   comment
                                              :comment/timestamp (java.util.Date.)}
                                             (creation-meta user))]})]})

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
