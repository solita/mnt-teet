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
   :authorization {:activity/edit-activity {}}
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
   :authorization {}
   :pre [(meeting-db/activity-meeting-id db activity-eid (:db/id form-data))
         (meeting-db/user-is-organizer-or-reviewer? db user (:db/id form-data))]
   :transact (let [{old-meeting-title :meeting/title
                    old-organizer :meeting/organizer}
                   (d/pull db '[:meeting/title :meeting/organizer]
                           (:db/id form-data))
                   new-organizer (get-in form-data [:meeting/organizer :db/id])]

               ;; New organizer must not already be a participant
               (when (and (not= (:db/id old-organizer)
                                (:db/id new-organizer))
                          (meeting-db/user-is-participating? db new-organizer (:db/id form-data)))
                 (db-api/fail! {:error :user-is-already-participant}))

               [(merge
                 (select-keys form-data [:db/id :meeting/organizer :meeting/title
                                         :meeting/start :meeting/end :meeting/location])
                 (meta-model/modification-meta user)
                 (when (not= old-meeting-title (:meeting/title form-data))
                   ;; Changing meeting title, we need to renumber the meeting
                   {:meeting/number (meeting-db/next-meeting-number
                                     db activity-eid (:meeting/title form-data))}))])})

(defcommand :meeting/delete
  {:doc "Delete existing meeting"
   :context {:keys [db user]}
   :payload {:keys [activity-eid meeting-id]}
   :project-id (project-db/meeting-project-id db meeting-id)
   :authorization {:activity/edit-activity {}}
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
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer? db user id)
         (agenda-items-new-or-belong-to-meeting db id agenda)]
   :transact [{:db/id id
               :meeting/agenda (mapv #(select-keys % [:db/id
                                                      :meeting.agenda/topic
                                                      :meeting.agenda/body
                                                      :meeting.agenda/responsible])
                                     agenda)}]})

(defcommand :meeting/delete-agenda
  {:doc "Mark given agenda topic as deleted"
   :context {:keys [db user]}
   :payload {agenda-id :agenda-id}
   :project-id (project-db/agenda-project-id db agenda-id)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
           db user
           (get-in (du/entity db agenda-id) [:meeting/_agenda :db/id]))]
   :transact [(meta-model/deletion-tx user agenda-id)]})

(defcommand :meeting/remove-participation
  {:doc "Remove a participation."
   :context {:keys [db user]}
   :payload {id :db/id}
   :project-id (project-db/meeting-project-id
                db
                (get-in (du/entity db id) [:participation/in :db/id]))
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
          db user
          (get-in (du/entity db id) [:participation/in :db/id]))]
   :transact [(meta-model/deletion-tx user id)]})

(defcommand :meeting/add-participation
  {:doc "Remove a participation."
   :context {:keys [db user]}
   :payload {meeting :participation/in
             participant :participation/participant :as participation}
   :project-id (project-db/meeting-project-id db meeting)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer? db user meeting)
         ^{:error :user-is-already-participant}
         (not (meeting-db/user-is-participating? db participant meeting))]
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

(defcommand :meeting/create-decision
  {:doc "Create a new decision under a topic"
   :context {:keys [db user]}
   :payload {:keys [agenda-eid form-data]}
   :project-id (project-db/agenda-project-id db agenda-eid)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
           db user
           (get-in (du/entity db agenda-eid) [:meeting/_agenda :db/id]))]
   :transact [{:db/id agenda-eid
               :meeting.agenda/decisions [(merge (select-keys form-data [:meeting.decision/body])
                                                 {:db/id "new decision"})]}]})

(defcommand :meeting/update-decision
  {:doc "Create a new decision under a topic"
   :context {:keys [db user]}
   :payload {:keys [form-data]}
   :project-id (project-db/decision-project-id db (:db/id form-data))
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
           db user
           (get-in (du/entity db (:db/id form-data))
                   [:meeting.agenda/_decisions :meeting/_agenda :db/id]))]
   :transact [(merge (select-keys form-data [:meeting.decision/body :db/id]))]})

(defcommand :meeting/delete-decision
  {:doc "Mark a given decision as deleted"
   :context {:keys [db user]}
   :payload {:keys [decision-id]}
   :project-id (project-db/decision-project-id db decision-id)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
           db user
           (get-in (du/entity db decision-id)
                   [:meeting.agenda/_decisions :meeting/_agenda :db/id]))]
   :transact [(meta-model/deletion-tx user decision-id)]})
