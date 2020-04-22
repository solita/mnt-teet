(ns teet.activity.activity-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.meta.meta-model :as meta-model]
            [teet.util.datomic :as du]
            [teet.project.project-db :as project-db]
            [teet.activity.activity-model :refer [all-tasks-completed?]]
            [teet.notification.notification-db :as notification-db]
            [datomic.client.api :as d]
            [teet.activity.activity-db :as activity-db])
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
                         [(get-else $ ?a :activity/actual-end-date ?time) ?end-date]
                         [(>= ?end-date ?time)]]
                    db
                    activity-name
                    lifecycle-id
                    (Date.))))))

(defn valid-tasks? [db tasks]
  (or (empty? tasks)
      (and (every? keyword? tasks)
           (let [task-keywords (into #{}
                                     (map first)
                                     (d/q '[:find ?id
                                            :where
                                            [?kw :enum/attribute :task/type]
                                            [?kw :db/ident ?id]]
                                          db))]
             (every? task-keywords tasks)))))

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
         (valid-tasks? db (map second tasks))]
   :transact [(merge
               {:db/id "new-activity"
                :activity/status :activity.status/in-preparation}
               (select-keys activity [:activity/name
                                      :activity/estimated-start-date
                                      :activity/estimated-end-date])
                (when (seq tasks)
                  {:activity/tasks
                   (vec
                    (for [[task-group task-type] tasks]
                      (merge {:db/id (str "NEW-TASK-"
                                          (name task-group) "-"
                                          (name task-type))
                              :task/estimated-end-date (:activity/estimated-end-date activity)
                              :task/estimated-start-date (:activity/estimated-start-date activity)
                              :task/status :task.status/not-started
                              :task/group task-group
                              :task/type task-type}
                             (meta-model/creation-meta user))))})
                (meta-model/creation-meta user))
              {:db/id lifecycle-id
               :thk.lifecycle/activities ["new-activity"]}]})

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

(defcommand :activity/delete
  {:doc "Mark an activity as deleted"
   :context {db :db
             user :user}
   :payload {activity-id :db/id}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:activity/delete-activity {}}
   :transact [(meta-model/deletion-tx user activity-id)]})


(defn check-tasks-are-complete [db activity-eid]
  (let [activity (d/pull db '[:activity/name :activity/status {:activity/tasks [:task/status]}] activity-eid)]
    (and (not-empty activity)
         (:activity/name activity)
         (all-tasks-completed? activity))))

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
                :target activity-id})]})

(defcommand :activity/review
  {:doc "Submit activity for review, when tasks are complete"
   :context {:keys [conn user db]}
   :payload {:keys [activity-id status]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:activity/change-activity-status {}}
   ;; precondition checks that 1. user is project owner, 2. new status is one of the permissible review outcomes 3. pre-existing status was :in-review
   :pre [(let [project-id (project-db/activity-project-id db activity-id)
               project-owner (project-db/project-owner db project-id)]
           ;; could this also be implemented with a :link authorization check?
           ;; 1
           (= (:db/id user) project-owner)
           ;; 3
           (= (->> activity-id (d/pull db '[:activity/status]) :activity/status :db/ident)
              :activity.status/in-review))
         ;; 2         
         (#{:activity.status/canceled
            :activity.status/archived
            :activity.status/completed} status)]
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
                :target activity-id})]})
