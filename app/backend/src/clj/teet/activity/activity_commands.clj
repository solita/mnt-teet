(ns teet.activity.activity-commands
  (:require [clojure.spec.alpha :as s]
            [datomic.client.api :as d]
            [teet.activity.activity-db :as activity-db]
            [teet.activity.activity-model :as activity-model]
            [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.meta.meta-model :as meta-model]
            [teet.notification.notification-db :as notification-db]
            [teet.project.project-db :as project-db]
            teet.project.project-specs
            [teet.task.task-db :as task-db]
            [teet.util.datomic :as du])
  (:import (java.util Date)))

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

(defn conflicting-activites?
  "Check if the lifecycle contains any activities withe the same name that have not ended yet"
  [db {activity-name :activity/name :as _activity} lifecycle-id]
  (boolean
    (seq
      (mapv first (d/q '[:find (pull ?a [*])
                         :in $ ?name ?lc ?time
                         :where
                         [?a :activity/name ?name]
                         [?lc :thk.lifecycle/activities ?a]
                         [(missing? $ ?a :meta/deleted?)]   ;; Don't take in to account deleted activities
                         [(get-else $ ?a :activity/actual-end-date ?time) ?end-date]
                         [(>= ?end-date ?time)]]
                    db
                    activity-name
                    lifecycle-id
                    (Date.))))))

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
         (not (conflicting-activites? db activity lifecycle-id))

         ^{:error :invalid-tasks}
         (valid-tasks? db (:activity/name activity) tasks)]
   :transact [(merge
               {:db/id "new-activity"
                :activity/status :activity.status/in-preparation}
               (select-keys activity [:activity/name
                                      :activity/estimated-start-date
                                      :activity/estimated-end-date])
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
               :thk.lifecycle/activities ["new-activity"]}]})

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
                                               :task/estimated-end-date estimated-end-date})]
   :transact (into [(merge
                     {:db/id id}
                     (meta-model/modification-meta user))]
                   (mapcat identity
                           (for [[task-group task-type send-to-thk?] tasks-to-add]
                             (let [id-placeholder (str "NEW-TASK-"
                                                       (name task-group) "-"
                                                       (name task-type))]
                               [(merge {:db/id id-placeholder
                                        :task/estimated-end-date estimated-start-date
                                        :task/estimated-start-date estimated-end-date
                                        :task/status :task.status/not-started
                                        :task/group task-group
                                        :task/type task-type
                                        :task/send-to-thk? send-to-thk?}
                                       (meta-model/creation-meta user))
                                [:db/add id :activity/tasks id-placeholder]]))))})

(defcommand :activity/update
  {:doc "Update activity basic info"
   :context {:keys [conn user db]}
   :payload {:keys [activity]}
   :project-id (project-db/activity-project-id db (:db/id activity))
   :authorization {:activity/edit-activity {:db/id (:db/id activity)}}
   :pre [^{:error :invalid-activity-dates}
         (valid-activity-dates? db
                                (activity-db/lifecycle-id-for-activity-id db (:db/id activity))
                                activity)]
   :transact [(merge (select-keys activity
                                  [:activity/estimated-start-date
                                   :activity/estimated-end-date
                                   :db/id])
                     (meta-model/modification-meta user))]})

(defn user-can-delete-activity?
  "A user can delete an activity if they created it less than half an hour ago."
  [db activity-id]
  (let [activity (d/pull db '[:activity/procurement-nr] activity-id)]
    (nil? (:activity/procurement-nr activity))))

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
  (let [activity (d/pull db '[:activity/name :activity/status {:activity/tasks [:task/status]}] activity-eid)]
    (and (not-empty activity)
         (:activity/name activity)
         (activity-model/all-tasks-completed? activity))))

(defcommand :activity/submit-for-review
  {:doc "Submit activity for review, when tasks are complete"
   :context {:keys [conn user db]}
   :payload {:keys [activity-id]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:task/submit-results {:eid (project-db/activity-project-id db activity-id)
                                         :link :thk.project/manager}}
   :pre [(check-tasks-are-complete db activity-id)]
   :transact [(merge
               {:db/id activity-id
                :activity/status :activity.status/in-review}
               (meta-model/modification-meta user))
              (notification-db/notification-tx
               {:from user
                :to (get-in (du/entity db (project-db/activity-project-id db activity-id))
                            [:thk.project/owner :db/id])
                :type :notification.type/activity-waiting-for-review
                :target activity-id
                :project (project-db/activity-project-id db activity-id)})]})

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
               {:from user
                :to (get-in (du/entity db (project-db/activity-project-id db activity-id))
                            [:thk.project/manager :db/id])
                :type (if (= status :activity.status/completed)
                        :notification.type/activity-accepted
                        ;; else archived or canceled (ensured by pre-check)
                        :notification.type/activity-rejected)
                :target activity-id
                :project (project-db/activity-project-id db activity-id)})]})
