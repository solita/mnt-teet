(ns teet.task.task-commands
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.project.project-db :as project-db]
            [teet.activity.activity-db :as activity-db]
            [teet.meta.meta-model :as meta-model]
            teet.task.task-spec
            [teet.util.collection :as uc]
            [teet.notification.notification-db :as notification-db]
            [teet.project.task-model :as task-model]
            [teet.util.datomic :as du]
            [clojure.spec.alpha :as s]
            [teet.task.task-db :as task-db]))

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
       (task-db/task-type-can-be-sent-to-thk? db type))))

(defn- new? [{id :db/id}]
  (string? id))

(defn assignment-notification-tx
  [user task project]
  (if-let [assignee (:task/assignee task)]
    (notification-db/notification-tx
      {:from user
       :to [:user/id (:user/id assignee)]
       :target (:db/id task)
       :type :notification.type/task-assigned
       :project project})
    {}))

(defn valid-task-dates?
  [db activity-id {:task/keys [actual-end-date actual-start-date estimated-end-date estimated-start-date] :as task}]
  (let [activity-dates (activity-db/activity-date-range db activity-id)
        dates (filterv some? [actual-end-date actual-start-date estimated-end-date estimated-start-date])]
    (every? (fn [date]
              (and (not (.before date (:activity/estimated-start-date activity-dates)))
                   (not (.after date (:activity/estimated-end-date activity-dates)))))
            dates)))

(defcommand :task/update
  {:doc "Update basic task information for existing task."
   :context {:keys [user db]} ; bindings from context
   :payload {id :db/id :as task} ; bindings from payload
   :project-id (project-db/task-project-id db id)
   :pre [^{:error :invalid-task-dates}
         (valid-task-dates? db (task-db/activity-for-task-id db id) task)
         (not (new? task))]
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
                  (assignment-notification-tx user task (project-db/task-project-id db id))
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
         (valid-thk-send? db task)
         ^{:error :invalid-task-dates}
         (valid-task-dates? db activity-id task)
         ^{:error :invalid-task-for-activity}
         (task-db/valid-task-for-activity? db activity-id task)]
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
              (assignment-notification-tx user task
                                          (project-db/activity-project-id db activity-id))]})

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
                :type :notification.type/task-waiting-for-review
                :project (project-db/task-project-id db task-id)})]})

(defcommand :task/start-review
  {:doc "Start review for task, sets status."
   :context {:keys [db user]}
   :payload {task-id :task-id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/review {:id task-id}}
   :pre [(task-model/waiting-for-review? (d/pull db [:task/status] task-id))]
   :transact [{:db/id task-id
               :task/status :task.status/reviewing}]})

(s/def ::task-id integer?)
(s/def ::result #{:accept :reject})

(defcommand :task/review
  {:spec (s/keys :req-un [::task-id ::result])
   :doc "Accept or reject review for task"
   :context {:keys [db user]}
   :payload {task-id :task-id
             result :result}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/review {:id task-id}}
   :pre [(task-model/reviewing? (d/pull db [:task/status] task-id))]
   :transact
   (case result
     ;; Accept: mark as completed and finalize files
     :accept (into
              [{:db/id task-id
                :task/status :task.status/completed}]

              ;; Mark all latest versions as final
              (for [{id :db/id} (task-db/files-for-task db task-id)]
                {:db/id id
                 :file/status :file.status/final}))

     ;; Reject: mark as in-progress and notify assignee
     :reject [{:db/id task-id
               :task/status :task.status/in-progress}
              (notification-db/notification-tx
               {:from user
                :to (get-in (du/entity db task-id)
                            [:task/assignee :db/id])
                :type :notification.type/task-review-rejected
                :target task-id
                :project (project-db/task-project-id db task-id)})])})
