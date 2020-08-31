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
            [teet.user.user-model :as user-model]
            [teet.meeting.meeting-db :as meeting-db]))

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
   :transact (let [meeting
                   (merge {:db/id "new-meeting"}
                          form-data
                          (meta-model/creation-meta user)
                          {:meeting/number (meeting-db/next-meeting-number
                                            db
                                            activity-eid
                                            (:meeting/title form-data))})]
               [{:db/id activity-eid
                 :activity/meetings [meeting]}])})

(defcommand :meeting/update
  {:doc "Update existing meeting"
   :context {:keys [db user]}
   :payload {:keys [activity-eid]
             :meeting/keys [form-data]}
   :project-id (project-db/activity-project-id db activity-eid)
   :authorization {:activity/create-activity {}             ;;TODO Actual authorization
                   }
   :pre [(meeting-db/activity-meeting-id db activity-eid (:db/id form-data))]
   :transact [(let [old-meeting-title (:meeting/title (du/entity db (:db/id form-data)))
                    meeting (merge
                              (select-keys form-data [:db/id :meeting/organizer :meeting/title
                                                      :meeting/start :meeting/end])
                              (meta-model/modification-meta user)
                              (when (not= old-meeting-title (:meeting/title form-data))
                                ;; Changing meeting title, we need to renumber the meeting
                                {:meeting/number (meeting-db/next-meeting-number
                                                  db activity-eid (:meeting/title form-data))}))]
                meeting)]})

(defcommand :meeting/delete
  {:doc "Delete existing meeting"
   :context {:keys [db user]}
   :payload {:keys [activity-eid meeting-id]}
   :project-id (project-db/activity-project-id db activity-eid)
   :authorization {:activity/delete-activity {}             ;; TODO actual authorization
                   }
   :pre [(meeting-db/activity-meeting-id db activity-eid meeting-id)]
   :transact [(meta-model/deletion-tx user meeting-id)]})

(defn- agenda-items-new-or-belong-to-meeting [db meeting-id agenda]
  (let [ids-to-update (remove string? (map :db/id agenda))
        existing-ids (meeting-db/meeting-agenda-ids db meeting-id)]
    (every? existing-ids ids-to-update)))

(defcommand :meeting/update-agenda
  {:doc "Add/update agenda item(s) in a meeting"
   :context {:keys [db user]}
   :payload {id :db/id
             agenda :meeting/agenda}
   :project-id (project-db/meeting-project-id db id)
    ;; TODO authorization matrix
   :authorization {:activity/edit-activity {}}
   :pre [(agenda-items-new-or-belong-to-meeting db id agenda)]
   :transact [{:db/id id
               :meeting/agenda (mapv #(select-keys % [:db/id
                                                      :meeting.agenda/topic
                                                      :meeting.agenda/body
                                                      :meeting.agenda/responsible])
                                     agenda)}]})
