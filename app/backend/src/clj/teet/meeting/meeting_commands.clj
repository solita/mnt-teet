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
            [teet.meeting.meeting-db :as meeting-db]
            [teet.meeting.meeting-ics :as meeting-ics]
            [teet.integration.integration-email :as integration-email]
            [teet.environment :as environment]
            [teet.meeting.meeting-model :as meeting-model]
            [teet.log :as log]
            [clojure.string :as str]))

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
                                                      :meeting/start :meeting/end :meeting/location])
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

(defcommand :meeting/remove-participation
  {:doc "Remove a participation."
   :context {:keys [db user]}
   :payload {id :db/id}
   :project-id (project-db/meeting-project-id
                db
                (get-in (du/entity db id) [:meeting/_participants :db/id]))
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
          db user
          (get-in (du/entity db id) [:meeting/_participants :db/id]))]
   :transact [(meta-model/deletion-tx user id)]})

(defcommand :meeting/add-participation
  {:doc "Remove a participation."
   :context {:keys [db user]}
   :payload {meeting :participation/in :as participation}
   :project-id (project-db/meeting-project-id db meeting)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer? db user meeting)]
   :transact [(merge {:db/id "new-participation"}
                     (select-keys participation [:participation/in
                                                 :participation/role
                                                 :participation/participant]))]})

(defcommand :meeting/send-notifications
  {:doc "Send iCalendar notifications to organizer and participants."
   :context {:keys [db user]}
   :payload {id :db/id}
   :project-id (project-db/meeting-project-id db id)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer? db user id)]}
  (let [to (remove #(str/ends-with? % "@example.com")
                   (keep :user/email (meeting-db/participants db id)))]
    (if (seq to)
      (let [meeting (d/pull db
                            '[:db/id
                              :meeting/number
                              :meeting/title :meeting/location
                              :meeting/start :meeting/end
                              {:meeting/agenda [:meeting.agenda/topic
                                                {:meeting.agenda/responsible [:user/given-name
                                                                              :user/family-name]}]}]
                            id)

            ics (meeting-ics/meeting-ics meeting)
            email-response
            (integration-email/send-email!
             {:from (environment/ssm-param :email :from)
              :to to
              :subject (meeting-model/meeting-title meeting)
              :parts [{:headers {"Content-Type" "text/calendar"}
                       :body ics}]})]
        (log/info "SES send response" email-response)
        :ok)
      {:error :no-participants-with-email})))
