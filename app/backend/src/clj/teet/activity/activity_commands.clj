(ns teet.activity.activity-commands
  (:require [clojure.spec.alpha :as s]
            [datomic.client.api :as d]
            [teet.activity.activity-db :as activity-db]
            [teet.activity.activity-model :as activity-model]
            [teet.db-api.core :as db-api :refer [defcommand tx-ret]]
            [teet.meta.meta-model :as meta-model]
            [teet.notification.notification-db :as notification-db]
            [teet.project.project-db :as project-db]
            teet.project.project-specs
            [teet.task.task-db :as task-db]
            [teet.util.datomic :as du]
            [teet.permission.permission-db :as permission-db]
            [teet.util.collection :as cu]
            [teet.user.user-model :as user-model]
            [teet.integration.integration-id :as integration-id]))

(defn valid-activity-name?
  "Check if the activity name is valid for the lifecycle it's being added to"
  [db {activity-name :activity/name} lifecycle-id]
  (boolean
    (seq
      (d/q '[:find ?lc
             :in $ ?lc ?act-enum
             :where
             [?act-enum :enum/valid-for ?v]
             [?lc :thk.lifecycle/type ?v]]
           db
           lifecycle-id
           activity-name))))

(defn valid-activity-dates?
  [db lifecycle-id {:activity/keys [estimated-start-date estimated-end-date]}]
  (let [lifecycle-dates (project-db/lifecycle-dates db lifecycle-id)
        dates (filterv some? [estimated-end-date estimated-start-date])]
    (every? (fn [date]
              (and (not (.before date (:thk.lifecycle/estimated-start-date lifecycle-dates)))
                   (not (.after date (:thk.lifecycle/estimated-end-date lifecycle-dates)))))
            dates)))

(defn- manager-notification-tx [db project-eid user manager]
  (notification-db/notification-tx
    db
    {:from user
     :to manager
     :target project-eid
     :type :notification.type/project-manager-assigned
     :project project-eid}))

(defn- manager-permission-tx [project-eid user manager]
  {:user/id (:user/id manager)
   :user/permissions [(merge
                       {:db/id "new-manager-permission"
                        :permission/valid-from (java.util.Date.)
                        :permission/role :ta-project-manager
                        :permission/projects [project-eid]}
                       (meta-model/creation-meta user))]})

(defn- ensure-manager-permission-tx
  "Check if given manager has the manager permission for the project and add it if missing.
  Returns tx data map to transact the permission or nil."
  [db project-eid user manager]
  (when-not (permission-db/has-permission? db manager project-eid :ta-project-manager)
    (manager-permission-tx project-eid user manager)))

(defcommand :activity/create
  {:doc "Create new activity to lifecycle"
   :context {:keys [db user conn]}
   :payload {:keys [activity lifecycle-id tasks]}
   :project-id (project-db/lifecycle-project-id db lifecycle-id)
   :contract-authorization {:action :activity/create-activity}
   :authorization {:activity/create-activity {}}
   :pre [^{:error :invalid-activity-name}
         (valid-activity-name? db activity lifecycle-id)

         ^{:error :invalid-activity-dates}
         (valid-activity-dates? db lifecycle-id activity)

         ^{:error :invalid-tasks}
         (task-db/valid-tasks? db (:activity/name activity) tasks)]
   ;; Audited as the command can grant privileges
   :audit? true}
  (let [manager (:activity/manager activity)
        project-id (project-db/lifecycle-project-id db lifecycle-id)]
    (tx-ret [(list 'teet.activity.activity-tx/ensure-activity-validity
                   lifecycle-id
                   [{:db/id lifecycle-id
                     :thk.lifecycle/activities
                     [(merge
                        {:db/id "new-activity"
                         :integration/id (integration-id/unused-random-small-uuid db)
                         :activity/status :activity.status/in-preparation}
                        (-> activity
                            (select-keys [:activity/name
                                          :activity/estimated-start-date
                                          :activity/estimated-end-date
                                          :activity/manager])
                            (cu/update-in-if-exists [:activity/manager] user-model/user-ref))
                        (when (seq tasks)
                          {:activity/tasks
                           (vec
                             (for [[task-group task-type send-to-thk?] tasks]
                               (merge {:db/id (str "NEW-TASK-"
                                                   (name task-group) "-"
                                                   (name task-type))
                                       :task/estimated-end-date (:activity/estimated-end-date activity)
                                       :task/estimated-start-date (:activity/estimated-start-date activity)
                                       :task/status :task.status/not-started
                                       :task/group task-group
                                       :task/type task-type
                                       :task/send-to-thk? send-to-thk?}
                                      (when send-to-thk?
                                        {:integration/id (integration-id/unused-random-small-uuid db)})
                                      (meta-model/creation-meta user))))})
                        (meta-model/creation-meta user))]}])]
            (when manager
              [(ensure-manager-permission-tx db
                                             project-id
                                             user
                                             manager)
               (manager-notification-tx db project-id user manager)]))))

(defcommand :activity/add-tasks
  {:doc "Add new tasks to activity"
   :context {:keys [db user conn]}
   :payload {:task/keys [estimated-start-date estimated-end-date]
             :activity/keys [tasks-to-add]
             :db/keys [id]}
   :project-id (project-db/activity-project-id db id)
   :authorization {:task/create-task {}
                   :activity/edit-activity {:db/id id}}
   :contract-authorization {:action :activity/edit-activity
                            :target id}
   :pre [^{:error :invalid-tasks}
         (let [activity-name (-> (du/entity db id) :activity/name :db/ident)]
           (task-db/valid-tasks? db activity-name tasks-to-add))

         ^{:error :invalid-task-dates}
         (activity-db/valid-task-dates? db id {:task/estimated-start-date estimated-start-date
                                               :task/estimated-end-date estimated-end-date})]
   :transact
   (let [activity-entity (du/entity db id)
         status (get-in activity-entity [:activity/status :db/ident])
         lifecycle-id (get-in activity-entity [:thk.lifecycle/_activities 0 :db/id])]
     [(list 'teet.activity.activity-tx/ensure-activity-validity
            lifecycle-id
            (into [(merge
                     {:db/id id}
                     (when (= status :activity.status/completed)
                       {:activity/status :activity.status/in-progress})
                     (meta-model/modification-meta user))]
                  (mapcat identity
                          (for [[task-group task-type send-to-thk?] tasks-to-add]
                            (let [id-placeholder (str "NEW-TASK-"
                                                      (name task-group) "-"
                                                      (name task-type))]
                              [(merge {:db/id id-placeholder
                                       :task/estimated-end-date estimated-end-date
                                       :task/estimated-start-date estimated-start-date
                                       :task/status :task.status/not-started
                                       :task/group task-group
                                       :task/type task-type
                                       :task/send-to-thk? send-to-thk?}
                                      (when send-to-thk?
                                        {:integration/id (integration-id/unused-random-small-uuid db)})
                                      (meta-model/creation-meta user))
                               [:db/add id :activity/tasks id-placeholder]])))))])})


(defcommand :activity/update
  {:doc "Update activity basic info"
   :context {:keys [conn user db]}
   :payload {:keys [activity]}
   :project-id (project-db/activity-project-id db (:db/id activity))
   :authorization {:activity/edit-activity {:db/id (:db/id activity)}}
   :contract-authorization {:action :activity/edit-activity
                            :target (:db/id activity)}
   :pre [^{:error :invalid-activity-dates}
         (valid-activity-dates? db
                                (activity-db/lifecycle-id-for-activity-id db (:db/id activity))
                                activity)]}
  (let [project-id (project-db/activity-project-id db (:db/id activity))
        new-manager (:activity/manager activity)
        current-manager-id (get-in (du/entity db (:db/id activity)) [:activity/manager :user/id])
        lifecycle-id (activity-db/lifecycle-id-for-activity-id db (:db/id activity))]
    (tx-ret [(list 'teet.activity.activity-tx/ensure-activity-validity
                   lifecycle-id
                   [(merge (-> activity
                               (select-keys [:activity/estimated-start-date
                                             :activity/estimated-end-date
                                             :activity/manager
                                             :db/id])
                               (cu/update-in-if-exists [:activity/manager] user-model/user-ref))
                           (meta-model/modification-meta user))])]
            (when (and new-manager
                       (not= (:user/id new-manager)
                             current-manager-id))
              [(ensure-manager-permission-tx db project-id user new-manager)
               (manager-notification-tx db project-id user new-manager)]))))

(defcommand :activity/delete
  {:doc "Mark an activity as deleted"
   :context {db :db
             user :user}
   :payload {activity-id :db/id}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:activity/delete-activity {}}
   :contract-authorization {:action :activity/delete-activity}
   :transact [(list 'teet.activity.activity-tx/delete-activity user activity-id)]})


(defn check-tasks-are-complete [db activity-eid]
  (let [activity (d/pull db '[:activity/name :activity/status {:activity/tasks [:task/status :meta/deleted?]}] activity-eid)]
    (and (not-empty activity)
         (:activity/name activity)
         (activity-model/all-tasks-completed? activity))))

(defcommand :activity/submit-for-review
  {:doc "Submit activity for review, when tasks are complete"
   :context {:keys [conn user db]}
   :payload {:keys [activity-id]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:task/submit-results {:eid activity-id
                                         :link :activity/manager}}
   :contract-authorization {:action :activity/submit-activity}
   :pre [(check-tasks-are-complete db activity-id)]
   :transact [(merge
                {:db/id activity-id
                 :activity/status :activity.status/in-review}
                (meta-model/modification-meta user))
              (if-let [owner (get-in (du/entity db (project-db/activity-project-id db activity-id))
                                     [:thk.project/owner :db/id])]
                (notification-db/notification-tx
                  db
                  {:from user
                   :to owner
                   :type :notification.type/activity-waiting-for-review
                   :target activity-id
                   :project (project-db/activity-project-id db activity-id)})
                {})]})

(s/def ::activity-id integer?)
(s/def ::status #{:activity.status/canceled
                  :activity.status/archived
                  :activity.status/completed})

(defcommand :activity/review
  {:doc "Submit review result for activity"
   :spec (s/and (s/keys :req-un [::activity-id ::status]))
   :context {:keys [conn user db]}
   :payload {:keys [activity-id status]}
   :project-id (project-db/activity-project-id db activity-id)

   ;; Require change activity status, link checks for project owner
   :authorization {:activity/change-activity-status
                   {:id (project-db/activity-project-id db activity-id)
                    :link :thk.project/owner}}
   :contract-authorization {:action :activity/review-activity}
   :pre [^{:error :invalid-activity-status}
         (= :activity.status/in-review
            (get-in (du/entity db activity-id)
                    [:activity/status :db/ident]))]
   :transact [(merge {:db/id activity-id
                      :activity/status status}
                     (meta-model/modification-meta user))
              (notification-db/notification-tx
                db
                {:from user
                 :to (get-in (du/entity db activity-id)
                             [:activity/manager :db/id])
                 :type (if (= status :activity.status/completed)
                         :notification.type/activity-accepted
                         ;; else archived or canceled (ensured by pre-check)
                         :notification.type/activity-rejected)
                 :target activity-id
                 :project (project-db/activity-project-id db activity-id)})]})
