(ns teet.task.task-commands
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.project.project-db :as project-db]
            [teet.meta.meta-model :as meta-model]
            teet.task.task-spec
            [teet.util.collection :as uc]))

(defn- send-to-thk? [db task-id]
  (:task/send-to-thk? (d/pull db [:task/send-to-thk?] task-id)))

(defcommand :task/delete
  {:doc "Mark a task as deleted"
   :context {db :db
             user :user}
   :payload {task-id :db/id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {} ;; FIXME: :task/delete
   :pre [(not (send-to-thk? db task-id))]
   :transact [(meta-model/deletion-tx user task-id)]})

(def ^:private always-selected-keys
  [:db/id :task/description :task/status :task/assignee
   :task/estimated-start-date :task/estimated-end-date])

(def ^:private thk-provided-keys
  [:task/actual-start-date :task/actual-end-date])

(defn select-update-keys
  "Select keys to update. The set of keys depends on whether the task is sent to THK."
  [task send-to-thk?]
  (select-keys task
               (if send-to-thk?
                 always-selected-keys
                 (concat always-selected-keys thk-provided-keys))))

(defn- valid-thk-send? [db {:task/keys [send-to-thk? type]}]
  (boolean
   (or (not send-to-thk?)
       (ffirst (d/q '[:find ?thk-type
                      :where [?t :thk/task-type ?thk-type]
                      :in $ ?t]
                    db type)))))

(defn- new? [{id :db/id}]
  (string? id))

(defcommand :task/update
  {:doc "Update basic task information for existing task."
   :context {:keys [user db]} ; bindings from context
   :payload {id :db/id :as task} ; bindings from payload
   :project-id (project-db/task-project-id db id)
   :pre [(not (new? task))]
   :authorization {:task/task-information {:db/id id
                                           :link :task/assignee}}  ; auth checks
   :transact [(merge (-> task
                         (select-update-keys (send-to-thk? db id))
                         uc/without-nils)
                     (meta-model/modification-meta user))]})

(def ^:private task-create-keys
  (into (concat always-selected-keys
                thk-provided-keys)
        [:task/group :task/type
         :task/send-to-thk?]))

(defcommand :task/create
  {:doc "Add task to activity"
   :context {:keys [db conn user]}
   :payload {activity-id :activity-id
             task        :task :as payload}
   :project-id (project-db/activity-project-id db activity-id)
   :pre [(new? task)
         (valid-thk-send? db task)]
   :authorization {:task/create-task {}
                   :activity/edit-activity {:db/id activity-id}}
   :transact [(merge {:db/id          activity-id
                      :activity/tasks [(merge (-> task
                                                  (select-keys task-create-keys)
                                                  (update :task/assignee (fn [{id :user/id}] [:user/id id])))
                                              (meta-model/creation-meta user))]})]})
