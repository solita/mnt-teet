(ns teet.task.task-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.project.project-db :as project-db]
            [teet.meta.meta-model :as meta-model]))

(defcommand :task/delete
  {:doc "Mark a task as deleted"
   :context {db :db
             user :user}
   :payload {task-id :db/id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {} ;; FIXME: :task/delete
   :transact [(meta-model/deletion-tx user task-id)]})

(defcommand :task/update
  {:doc "Update basic task information for existing task."
   :context {:keys [user db]} ; bindings from context
   :payload {id :db/id :as task} ; bindings from payload
   :project-id (project-db/task-project-id db id)
   :authorization {:task/edit-task {:db/id id
                                    :link :task/assignee}}  ; auth checks
   :transact [(merge (select-keys task
                                  [:db/id :task/name :task/description :task/status :task/assignee])
                     (meta-model/modification-meta user))]})
