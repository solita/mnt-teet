(ns teet.task.task-commands
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.project.project-db :as project-db]
            [teet.activity.activity-db :as activity-db]
            [teet.activity.activity-model :as activity-model]
            [teet.meta.meta-model :as meta-model]
            teet.task.task-spec
            [teet.util.collection :as uc]
            [teet.notification.notification-db :as notification-db]
            [teet.project.task-model :as task-model]
            [teet.util.datomic :as du]
            [clojure.spec.alpha :as s]
            [teet.task.task-db :as task-db]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [teet.environment :as environment]
            [teet.authorization.authorization-core :as authorization]))

(defn allow-delete?
  "Check extra access for deleting task that has been sent to THK"
  [db user task-id]
  (if (and (task-db/send-to-thk? db task-id)
           (not (authorization-check/authorized? user :task/delete-task-sent-to-thk)))
    false
    true))

(defcommand :task/delete
  {:doc "Mark a task as deleted"
   :context {db :db
             user :user}
   :payload {task-id :db/id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/delete-task {}}
   :pre [(allow-delete? db user task-id)]
   :transact [(list 'teet.task.task-tx/delete-task user task-id)]})

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

(defn- new? [{id :db/id}]
  (string? id))

(defn assignment-notification-tx
  [db user task project]
  (if-let [assignee (:task/assignee task)]
    (notification-db/notification-tx
      db
      {:from user
       :to [:user/id (:user/id assignee)]
       :target (:db/id task)
       :type :notification.type/task-assigned
       :project project})
    {}))

(defn review-notification-tx
  [db from role target type task-id]
  (if-let [to (case role
                :assignee (task-db/get-task-assignee-by-id db task-id)
                :manager (activity-db/activity-manager db (activity-db/task-activity-id db task-id)))]
    (notification-db/notification-tx
      db
      {:from from
       :to to
       :target target
       :type type
       :project (project-db/task-project-id db task-id)})
    {}))

(defn taskpart-file-tx
  [db user taskpart-id status]
  (for [{id :db/id} (task-db/task-part-file-listing db user taskpart-id)]
    {:db/id id
     :file/status status}))

(defn not-reviewed-files-and-parts-tx
  "Updates the task part and file status under this task that have not been separately reviewed/completed."
  [db user task-id file-status part-status]
  (concat (for [{id :db/id} (task-db/not-reviewed-task-files db user task-id)]
            {:db/id id
             :file/status file-status})
          (for [pid (task-db/not-reviewed-file-parts db task-id)]
            {:db/id (first pid)
             :file.part/status part-status})))

(defn waiting-for-review-parts-tx
  "Creates the tx records to update task part(s) from waiting for review to reviewing"
  [task-parts]
  (map (fn [part]
                 {:db/id (:db/id part)
                  :file.part/status :file.part.status/reviewing})
               (filter
                 (fn [x] (= :file.part.status/waiting-for-review (:db/ident (:file.part/status x))))
                 (:file.part/_task task-parts))))

(defcommand :task/update
  {:doc "Update basic task information for existing task."
   :context {:keys [user db]} ; bindings from context
   :payload {id :db/id :as task} ; bindings from payload
   :project-id (project-db/task-project-id db id)
   :pre [^{:error :invalid-task-dates}
         (activity-db/valid-task-dates? db (task-db/activity-for-task-id db id) task)
         (not (new? task))]
   :authorization {:task/task-information {:db/id id
                                           :link :task/assignee}}  ; auth checks
   :transact
   (db-api-large-text/store-large-text!
    #{:task/description}
    (let [task* (du/entity db id)
          new-assignee-id (get-in task [:task/assignee :user/id])
          old-assignee-id (get-in task* [:task/assignee :user/id])
          assign? (not= new-assignee-id old-assignee-id)]
      [(merge
        (when (and (not old-assignee-id)
                   new-assignee-id)
          {:task/status :task.status/in-progress})
        (-> task
            (select-update-keys (task-db/send-to-thk? db id))
            uc/without-nils)
        (meta-model/modification-meta user))
       (if assign?
         (assignment-notification-tx db user task (project-db/task-project-id db id))
         {})
       (if assign?
         ;; If assigning, set activity status to in-progress
         {:db/id (get-in task* [:activity/_tasks 0 :db/id])
          :activity/status :activity.status/in-progress}
         {})]))})

(defcommand :task/submit
  {:doc "Submit task results for review."
   :context {:keys [db user]}
   :payload {task-id :task-id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/submit-results {:eid task-id
                                         :link :task/assignee}} ;; TODO add pre check to check that the task being submitted contains files
   :transact (into
               [{:db/id task-id
                 :task/status :task.status/waiting-for-review}
                (review-notification-tx db user :manager task-id :notification.type/task-waiting-for-review task-id)]
               (not-reviewed-files-and-parts-tx db user task-id :file.status/submitted :file.part.status/waiting-for-review))})

(defcommand :task/review-task-part
  {:doc "Submit task part for review and approve/reject."
   :context {:keys [db user]}
   :payload {task-id :task-id
             taskpart-id :taskpart-id
             result :result}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/review {:eid task-id
                                 :link :task/assignee}}
   :transact
   (case result
     :accept (into
               [(merge {:db/id taskpart-id
                        :file.part/status :file.part.status/completed}
                       (task-db/task-part-completion-attributes user))
                (review-notification-tx db user :assignee taskpart-id :notification.type/task-part-review-accepted task-id)]
               (taskpart-file-tx db user taskpart-id :file.status/final))
     :reject (into
               [{:db/id taskpart-id
                 :file.part/status :file.part.status/in-progress}
                (review-notification-tx db user :assignee taskpart-id :notification.type/task-part-review-rejected task-id)]
               (taskpart-file-tx db user taskpart-id :file.status/returned)))})

(defcommand :task/submit-task-part
  {:doc "Submit task part for review and approve/reject."
   :context {:keys [db user]}
   :payload {task-id :task-id
             taskpart-id :taskpart-id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/submit-results {:eid task-id
                                 :link :task/assignee}}
   :transact (into
               [{:db/id taskpart-id
                 :file.part/status :file.part.status/waiting-for-review}
                (review-notification-tx db user :manager taskpart-id :notification.type/task-part-waiting-for-review task-id)]
               (taskpart-file-tx db user taskpart-id :file.status/submitted))})

(defcommand :task/reopen-task-part
  {:doc "Reopen task part"
   :context {:keys [db user]}
   :payload {task-id :task-id
             taskpart-id :taskpart-id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/reopen {:eid task-id
                                 :link :task/assignee}}
   :pre [(task-model/part-completed? (d/pull db [:file.part/status] taskpart-id))]
   :transact (into
               [{:db/id taskpart-id
                 :file.part/status :file.part.status/in-progress}]
               (concat
                 [[:db/retract taskpart-id :file.part/completed-at]
                  [:db/retract taskpart-id :file.part/completed-by]]
                 (taskpart-file-tx db user taskpart-id :file.status/draft)))})

(defcommand :task/reopen-task
  {:doc "Reopen task"
   :context {:keys [db user]}
   :payload {task-id :task-id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/reopen {:eid task-id
                                 :link :task/assignee}}
   :pre [(and
           (activity-model/in-progress? (d/pull db [:activity/status] (task-db/activity-for-task-id db task-id)))
           (task-model/completed? (d/pull db [:task/status] task-id)))]
   :transact (into
               [{:db/id task-id
                 :task/status :task.status/in-progress}]
               (not-reviewed-files-and-parts-tx db user task-id :file.status/draft :file.part.status/in-progress))})

(defcommand :task/create-part
  {:doc "Create a new 'part' aka folder for files to be put in"
   :context {:keys [db user]}
   :payload {task-id :task-id
             part-name :part-name}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:document/upload-document {:db/id task-id
                                              :link :task/assignee}}
   :transact [(list 'teet.file.file-tx/create-task-file-part
                    user task-id part-name)]})

(defcommand :task/edit-part
  {:doc "Edit the name of an existing part"
   :context {:keys [db user]}
   :payload {task-id :task-id
             part-name :part-name
             part-id :part-id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:document/upload-document {:db/id task-id :link :task/assignee}}
   :contract-authorization {:action :file-management/edit-task-part
                            :target task-id}
   :transact [(merge
                (meta-model/modification-meta user)
                {:db/id part-id                             ;; todo check that this is actually a part
                 :file.part/name part-name})]})

(defcommand :task/delete-part
  {:doc "Delete the given part"
   :context {:keys [db user]}
   :payload {part-id :part-id}
   :project-id (project-db/file-part-project-id db part-id)
   :authorization {:task/task-information {:db/id (project-db/file-part-project-id db part-id)
                                           :link :task/assignee}}
   :transact [(list 'teet.file.file-tx/remove-task-file-part
                    part-id user)]})

(defcommand :task/start-review
  {:doc "Start review for task and/or task part, sets status to 'in review'."
   :context {:keys [db user]}
   :payload {task-id :task-id}
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/review {:id task-id}}
   :transact (if (or
                   (task-model/waiting-for-review? (d/pull db [:task/status] task-id))
                   (task-model/any-task-part-waiting-for-review? (task-db/task-file-parts db task-id)))
               (into
                 [(if (task-model/waiting-for-review? (d/pull db [:task/status] task-id))
                    {:db/id task-id
                     :task/status :task.status/reviewing}
                    {})]
                 (not-reviewed-files-and-parts-tx db user task-id :file.status/submitted :file.part.status/reviewing))
               (into [] (waiting-for-review-parts-tx (task-db/task-file-parts db task-id))))})

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
               (not-reviewed-files-and-parts-tx db user task-id :file.status/final :file.part.status/completed))

     ;; Reject: mark as in-progress and notify assignee
     :reject (into
               [{:db/id task-id
                 :task/status :task.status/in-progress}
                (review-notification-tx db user :assignee task-id :notification.type/task-review-rejected task-id)]
               (not-reviewed-files-and-parts-tx db user task-id :file.status/returned :file.part.status/in-progress)))})
