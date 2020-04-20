(ns teet.task.task-commands
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.project.project-db :as project-db]
            [teet.meta.meta-model :as meta-model]
            teet.task.task-spec
            [teet.util.collection :as uc]
            [teet.notification.notification-db :as notification-db]
            [teet.project.task-model :as task-model]
            [teet.util.datomic :as du]))

(defn- send-to-thk? [db task-id]
  (:task/send-to-thk? (d/pull db [:task/send-to-thk?] task-id)))

(defcommand :task/delete
  {:doc "Mark a task as deleted"
   :context {db :db
             user :user}
   :payload {task-id :db/id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/delete-task {}}
   :pre [(not (send-to-thk? db task-id))]
   :transact [(meta-model/deletion-tx user task-id)]})

(def ^:private always-selected-keys
  [:db/id :task/description :task/assignee
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

(defn assignment-notification-tx
  [user task]
  (if-let [assignee (:task/assignee task)]
    (notification-db/notification-tx
      {:from user
       :to [:user/id (:user/id assignee)]
       :target (:db/id task)
       :type :notification.type/task-assigned})
    {}))

(defcommand :task/update
  {:doc "Update basic task information for existing task."
   :context {:keys [user db]} ; bindings from context
   :payload {id :db/id :as task} ; bindings from payload
   :project-id (project-db/task-project-id db id)
   :pre [(not (new? task))]
   :authorization {:task/task-information {:db/id id
                                           :link :task/assignee}}  ; auth checks
   :transact (let [task* (du/entity db id)
                   new-assignee-id (get-in task [:task/assignee :user/id])
                   old-assignee-id (get-in task* [:task/assignee :user/id])
                   assign? (not= new-assignee-id old-assignee-id)]
               [(merge
                 (when (and (not old-assignee-id)
                            new-assignee-id)
                   {:task/status :task.status/in-progress})
                 (-> task
                     (select-update-keys (send-to-thk? db id))
                     uc/without-nils)
                 (meta-model/modification-meta user))
                (if assign?
                  (assignment-notification-tx user task)
                  {})
                (if assign?
                  ;; If assigning, set activity status to in-progress
                  {:db/id (get-in task* [:activity/_tasks 0 :db/id])
                   :activity/status :activity.status/in-progress}
                  {})])})

(def ^:private task-create-keys
  (into (concat always-selected-keys
                thk-provided-keys)
        [:task/group :task/type
         :task/send-to-thk?]))

(defcommand :task/create
  {:doc "Add task to activity"
   :context {:keys [db conn user]}
   :payload {activity-id :activity-id
             task :task :as payload}
   :project-id (project-db/activity-project-id db activity-id)
   :pre [(new? task)
         (valid-thk-send? db task)]
   :authorization {:task/create-task {}
                   :activity/edit-activity {:db/id activity-id}}
   :transact [(merge
               (when (:task/assignee task)
                 {:activity/status :activity.status/in-progress})
               {:db/id activity-id
                :activity/tasks
                [(merge (-> task
                            (select-keys task-create-keys))
                        (if (seq? (:task/assignee task))
                          {:task/status :task.status/in-progress
                           :task/assignee [:user/id (:user/id (:task/assignee task))]}
                          {:task/status :task.status/not-started})
                        (meta-model/creation-meta user))]})
              (assignment-notification-tx user task)]})

(defcommand :task/submit
  {:doc "Submit task results for review."
   :context {:keys [db user]}
   :payload {task-id :task-id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/submit-results {:eid task-id
                                         :link :task/assignee}}
   :transact [{:db/id task-id
               :task/status :task.status/waiting-for-review}
              (notification-db/notification-tx
               {:from user
                :to (project-db/project-owner db (project-db/task-project-id db task-id))
                :target task-id
                :type :notification.type/task-waiting-for-review})]})

(defcommand :task/review
  {:doc "Accept or reject review for task"
   :context {:keys [db user]}
   :payload {task-id :task-id
             status :status}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/review {:id task-id}}
   :pre [(task-model/waiting-for-review? (d/pull db [:task/status] task-id))
         (task-model/review-outcome-statuses status)]
   :transact [{:db/id task-id
               :task/status status}]})
