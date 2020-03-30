(ns teet.task.task-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.project.project-db :as project-db]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as uc]))

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
   :authorization {:task/task-information {:db/id id
                                           :link :task/assignee}}  ; auth checks
   ;; TODO check that type is valid for group
   ;; TODO how to remove e.g. end date?
   :transact [(merge (-> task
                         (select-keys [:db/id :task/name :task/description
                                       :task/group :task/type
                                       :task/status :task/assignee
                                       :task/estimated-start-date :task/estimated-end-date
                                       :task/actual-start-date :task/actual-end-date])
                         (uc/without-nils))
                     (meta-model/modification-meta user))]})

(defcommand :task/create
  {:doc "Add task to activity"
   :context {:keys [db conn user]}
   :payload {activity-id :activity-id
             task        :task :as payload}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:task/create-task {}
                   :activity/edit-activity {:db/id activity-id}}
   :transact [(merge {:db/id          activity-id
                      :activity/tasks [task]}
                     (meta-model/creation-meta user))]})
