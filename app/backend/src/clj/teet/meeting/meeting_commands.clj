(ns teet.meeting.meeting-commands
  (:require [clojure.spec.alpha :as s]
            [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defcommand tx-ret]]
            [teet.meta.meta-model :as meta-model]
            [teet.notification.notification-db :as notification-db]
            [teet.project.project-db :as project-db]
            teet.meeting.meeting-specs
            [teet.util.datomic :as du]
            [teet.util.collection :as cu]
            [teet.user.user-model :as user-model]))

;; TODO query all activity meetings
;; matching name found
;; TODO try tx function
(defcommand :meeting/create
  {:doc "Create new meetings to activities"
   :context {:keys [db user]}
   :payload {:keys [activity-eid]
             :meeting/keys [form-data]}
   :project-id (project-db/activity-project-id db activity-eid)
   :authorization {:activity/create-activity {}}            ;;TODO ADD ACTUAL AUTHORIZATION
   :transact (let [meeting (merge {:db/id "new-meeting"}
                                  form-data
                                  (meta-model/creation-meta user))]
               [{:db/id activity-eid
                 :activity/meetings [meeting]}])})
