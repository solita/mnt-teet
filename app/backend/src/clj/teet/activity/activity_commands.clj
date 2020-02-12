(ns teet.activity.activity-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.meta.meta-model :as meta-model]
            [teet.project.project-db :as project-db]
            [datomic.client.api :as d]
            [teet.log :as log])
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
  [db {activity-name :activity/name :as activity} lifecycle-id]
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

(defcommand :activity/create
  {:doc "Create new activity to lifecycle"
   :context {:keys [db user conn]}
   :payload {activity :activity
             lifecycle-id :lifecycle-id}
   :project-id (project-db/lifecycle-project-id db lifecycle-id)
   :authorization {:activity/create-activity {}}
   :pre [^{:error :invalid-activity-name}
         (valid-activity-name? db activity lifecycle-id)

         ^{:error :conflicting-activities}
         (not (conflicting-activites? db activity lifecycle-id))]
   :transact [(merge
                {:db/id "new-activity"}
                (select-keys activity [:activity/name :activity/status
                                       :activity/estimated-start-date
                                       :activity/estimated-end-date])
                (meta-model/creation-meta user))
              {:db/id lifecycle-id
               :thk.lifecycle/activities ["new-activity"]}]})

(defcommand :activity/update
  {:doc "Update activity basic info"
   :context {:keys [conn user db]}
   :payload activity
   :project-id (project-db/activity-project-id db (:db/id activity))
   :authorization {:activity/edit-activity {:db/id (:db/id activity)}}
   :pre [(valid-activity-name? db activity
                               (ffirst (d/q '[:find ?lc
                                              :in $ ?act
                                              :where [?lc :thk.lifecycle/activities ?act]]
                                            db
                                            (:db/id activity))))]
   :transact [(merge (select-keys activity
                                  [:activity/name :activity/status
                                   :activity/estimated-start-date
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
