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
            [teet.user.user-model :as user-model]))

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

(defn- valid-task-triple? [[t-group t-type send-to-thk? :as task-triple]]
  (and (= (count task-triple) 3)
       (keyword? t-group)
       (keyword? t-type)
       (boolean? send-to-thk?)))

(defn- valid-task-types? [db tasks]
  (let [task-keywords (into #{}
                            (map first)
                            (d/q '[:find ?id
                                   :where
                                   [?kw :enum/attribute :task/type]
                                   [?kw :db/ident ?id]]
                                 db))]
    (every? task-keywords
            (map second tasks))))

(defn- valid-task-groups-for-activity? [activity-name tasks]
  (every? (get activity-model/activity-name->task-groups activity-name #{})
          (map first tasks)))

(defn- valid-tasks-sent-to-thk? [db tasks]
  (->> tasks
       (filter (fn [[_ _ sent-to-thk?]]
                 sent-to-thk?))
       (map second)
       (task-db/task-types-can-be-sent-to-thk? db)))

(defn valid-tasks? [db activity-name tasks]
  (or (empty? tasks)
      (and (every? valid-task-triple? tasks)
           (valid-task-types? db tasks)
           (valid-task-groups-for-activity? activity-name tasks)
           (valid-tasks-sent-to-thk? db tasks))))

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
                        :permission/role :manager
                        :permission/projects [project-eid]}
                       (meta-model/creation-meta user))]})

(defn- ensure-manager-permission-tx
  "Check if given manager has the manager permission for the project and add it if missing.
  Returns tx data map to transact the permission or nil."
  [db project-eid user manager]
  (when-not (permission-db/has-permission? db manager project-eid :manager)
    (manager-permission-tx project-eid user manager)))

(defcommand :activity/create
  {:doc "Create new activity to lifecycle"
   :context {:keys [db user conn]}
   :payload {:keys [activity lifecycle-id tasks]}
   :project-id (project-db/lifecycle-project-id db lifecycle-id)
   :authorization {:activity/create-activity {}}
   :pre [^{:error :invalid-activity-name}
         (valid-activity-name? db activity lifecycle-id)

         ^{:error :invalid-activity-dates}
         (valid-activity-dates? db lifecycle-id activity)

         ^{:error :conflicting-activities}
         (not (activity-db/conflicting-activities? db activity lifecycle-id))

         ^{:error :invalid-tasks}
         (valid-tasks? db (:activity/name activity) tasks)]}

  (let [manager (:activity/manager activity)
        project-id (project-db/lifecycle-project-id db lifecycle-id)]
    (tx-ret [(merge
              {:db/id "new-activity"
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
                           (meta-model/creation-meta user))))})
              (meta-model/creation-meta user))
             {:db/id lifecycle-id
              :thk.lifecycle/activities ["new-activity"]}]
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
   :pre [^{:error :invalid-tasks}
         (let [activity-name (-> (du/entity db id) :activity/name :db/ident)]
           (valid-tasks? db activity-name tasks-to-add))

         ^{:error :invalid-task-dates}
         (activity-db/valid-task-dates? db id {:task/estimated-start-date estimated-start-date
                                               :task/estimated-end-date estimated-end-date})

         ^{:error :invalid-task-for-activity}
         (let [allowed-groups (activity-db/allowed-task-groups db id)]
           (every? allowed-groups (map first tasks-to-add)))]
   :transact
   (let [status (get-in (du/entity db id) [:activity/status :db/ident])]
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
                               (meta-model/creation-meta user))
                        [:db/add id :activity/tasks id-placeholder]])))))})


(defcommand :activity/update
  {:doc "Update activity basic info"
   :context {:keys [conn user db]}
   :payload {:keys [activity]}
   :project-id (project-db/activity-project-id db (:db/id activity))
   :authorization {:activity/edit-activity {:db/id (:db/id activity)}}
   :pre [^{:error :invalid-activity-dates}
         (valid-activity-dates? db
                                (activity-db/lifecycle-id-for-activity-id db (:db/id activity))
                                activity)

         ^{:error :conflicting-activities}
         (not (activity-db/conflicting-activities? db activity (activity-db/lifecycle-id-for-activity-id db (:db/id activity))))]}
  (let [project-id (project-db/activity-project-id db (:db/id activity))
        new-manager (:activity/manager activity)
        current-manager-id (get-in (du/entity db project-id) [:activity/manager :user/id])]
    (tx-ret [(merge (-> activity
                        (select-keys [:activity/estimated-start-date
                                      :activity/estimated-end-date
                                      :activity/manager
                                      :db/id])
                        (cu/update-in-if-exists [:activity/manager] user-model/user-ref))
                    (meta-model/modification-meta user))]
            (when new-manager
              [(ensure-manager-permission-tx db project-id user new-manager)
               (when (not= (:user/id new-manager)
                           current-manager-id)
                 (manager-notification-tx db project-id user new-manager))]))))

(defn user-can-delete-activity?
  "A user can delete an activity if it has no procurement number"
  [db activity-id]
  (activity-model/deletable? (du/entity db activity-id)))

(defcommand :activity/delete
  {:doc "Mark an activity as deleted"
   :context {db :db
             user :user}
   :payload {activity-id :db/id}
   :pre [^{:error :can-not-delete}
         (user-can-delete-activity? db activity-id)]
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:activity/delete-activity {}}
   :transact [(meta-model/deletion-tx user activity-id)]})


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
