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
   :payload {:keys [entity-id entity-type comment files]}
   :project-id (project-db/entity-project-id db entity-type entity-id)
   :authorization {:task/comment-task {:db/id entity-id}}
   :transact [(merge {:db/id entity-id
                      (comment-model/comments-attribute-for-entity-type entity-type)
                      [(merge {:db/id "new-comment"
                               :comment/author [:user/id (:user/id user)]
                               :comment/comment comment
                               :comment/timestamp (Date.)}
                              (creation-meta user)
                              (when (seq files)
                                {:comment/files (validate-files db user files)}))]})]})

(defn- comment-parent-entity [db comment-id]
  (if-let [file-id (ffirst
                    (d/q '[:find ?file
                           :in $ ?comment
                           :where [?file :file/comments ?comment]]
                         db comment-id))]
    [:file file-id]
    (if-let [task-id (ffirst
                      (d/q '[:find ?task
                             :in $ ?comment
                             :where [?task :task/comments ?comment]]
                           db comment-id))]
      [:task task-id]
      nil)))

(defn- get-project-id-of-comment [db comment-id]
  (let [[parent-type parent-id] (comment-parent-entity db comment-id)]
    (case parent-type
      :file (project-db/file-project-id db parent-id)
      :task (project-db/task-project-id db parent-id)
      (db-api/bad-request! "No such comment"))))

(defcommand :comment/update
  {:doc "Update existing comment"
   :context {:keys [db user]}
   :payload {comment-id :db/id comment :comment/comment}
   :project-id (get-project-id-of-comment db comment-id)
   :authorization {:document/edit-comment {:db/id comment-id}}
   :transact [(merge {:db/id comment-id
                      :comment/comment comment}
                     (modification-meta user))]})

(defcommand :comment/delete-comment
  {:doc "Delete existing comment"
   :context {:keys [db user]}
   :payload {:keys [comment-id]}
   :project-id (get-project-id-of-comment db comment-id)
   :authorization {:document/delete-comment {:db/id comment-id}}
   :transact [(deletion-tx user comment-id)]})
