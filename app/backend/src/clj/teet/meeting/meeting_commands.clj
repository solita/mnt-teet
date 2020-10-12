(ns teet.meeting.meeting-commands
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defcommand tx-ret]]
            [teet.meta.meta-model :as meta-model]
            [teet.file.file-db :as file-db]
            [teet.project.project-db :as project-db]
            teet.meeting.meeting-specs
            [teet.util.datomic :as du]
            [teet.meeting.meeting-db :as meeting-db]
            [teet.meeting.meeting-ics :as meeting-ics]
            [teet.integration.integration-email :as integration-email]
            [teet.environment :as environment]
            [teet.meeting.meeting-model :as meeting-model]
            [teet.log :as log]
            [clojure.string :as str]
            [teet.user.user-model :as user-model]
            [teet.util.date :refer [date-in-past?]]
            [teet.link.link-db :as link-db]
            [teet.notification.notification-db :as notification-db])
  (:import (java.util Date))) 

(defn update-meeting-tx
  [meeting-id tx-data]
  [(list 'teet.meeting.meeting-tx/update-meeting
         meeting-id
         tx-data)])

(defmethod file-db/attach-to :meeting-agenda
  [db user _file [_ meeting-agenda-id]]
  (let [meeting-id (meeting-db/agenda-meeting-id db meeting-agenda-id)]
    (when (meeting-db/user-is-organizer-or-reviewer? db user meeting-id)
      {:eid meeting-agenda-id
       :wrap-tx #(update-meeting-tx meeting-id %)})))

(defmethod file-db/allow-download-attachments? :meeting-agenda
  [db user [_ meeting-agenda-id]]
  (meeting-db/user-is-participating?
   db user (meeting-db/agenda-meeting-id db meeting-agenda-id)))

(defmethod file-db/delete-attachment :meeting-agenda
  [db user file-id [_ meeting-agenda-id]]
  (let [meeting-id (meeting-db/agenda-meeting-id db meeting-agenda-id)]
    (if (meeting-db/user-is-organizer-or-reviewer? db user meeting-id)
      (update-meeting-tx meeting-id [(meta-model/deletion-tx user file-id)])
      (throw (ex-info "Unauthorized attachment delete"
                      {:error :unauthorized})))))

(defmethod file-db/attach-to :meeting-decision
  [db user _file [_ meeting-decision-id]]
  (let [meeting-id (meeting-db/decision-meeting-id db meeting-decision-id)]
    (when (meeting-db/user-is-organizer-or-reviewer? db user meeting-id)
      {:eid meeting-decision-id
       :wrap-tx #(update-meeting-tx meeting-id %)})))

(defmethod file-db/allow-download-attachments? :meeting-decision
  [db user [_ meeting-decision-id]]
  (meeting-db/user-is-participating?
   db user
   (meeting-db/decision-meeting-id db meeting-decision-id)))

(defmethod file-db/delete-attachment :meeting-decision
  [db user file-id [_ meeting-decision-id]]
  (let [meeting-id (meeting-db/decision-meeting-id db meeting-decision-id)]
    (if (meeting-db/user-is-organizer-or-reviewer? db user meeting-id)
      (update-meeting-tx meeting-id
                         [(meta-model/deletion-tx user file-id)])
      (throw (ex-info "Unauthorized attachment delete"
                      {:error :unauthorized})))))


(defn- link-from-meeting [db user from]
  (let [meeting-id (meeting-db/link-from->meeting db from)]
    (when (meeting-db/user-is-organizer-or-reviewer? db user meeting-id)
      {:wrap-tx #(update-meeting-tx meeting-id %)})))

(defmethod link-db/link-from [:meeting-agenda :task]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/link-from [:meeting-decision :task]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/link-from [:meeting-agenda :cadastral-unit]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/link-from [:meeting-agenda :estate]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/link-from [:meeting-decision :cadastral-unit]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/link-from [:meeting-decision :estate]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/delete-link-from [:meeting-agenda :task]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/delete-link-from [:meeting-decision :task]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/delete-link-from [:meeting-agenda :cadastral-unit]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/delete-link-from [:meeting-agenda :estate]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/delete-link-from [:meeting-decision :cadastral-unit]
  [db user from _type _to]
  (link-from-meeting db user from))

(defmethod link-db/delete-link-from [:meeting-decision :estate]
  [db user from _type _to]
  (link-from-meeting db user from))


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
   :transact (update-meeting-tx
               (:db/id form-data)
               (let [{old-meeting-title :meeting/title
                      old-organizer :meeting/organizer}
                     (d/pull db '[:meeting/title :meeting/organizer]
                             (:db/id form-data))
                     new-organizer (get-in form-data [:meeting/organizer])]

                 ;; New organizer must not already be a participant
                 ;; PENDING: these could be done in the update-meeting-tx db fn
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
                                         db activity-eid (:meeting/title form-data))}))]))})

(defn- agenda-items-new-or-belong-to-meeting [db meeting-id agenda]
  (let [ids-to-update (remove string? (map :db/id agenda))
        existing-ids (meeting-db/meeting-agenda-ids db meeting-id)]
    (every? existing-ids ids-to-update)))

(defcommand :meeting/update-agenda
  {:doc "Add/update agenda item(s) in a meeting"
   :context {:keys [db user]}
   :payload {meeting-id :db/id
             agenda :meeting/agenda}
   :project-id (project-db/meeting-project-id db meeting-id)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer? db user meeting-id)
         (agenda-items-new-or-belong-to-meeting db meeting-id agenda)]
   :transact (update-meeting-tx
               meeting-id
               [{:db/id meeting-id
                 :meeting/agenda (mapv #(select-keys % [:db/id
                                                        :meeting.agenda/topic
                                                        :meeting.agenda/body
                                                        :meeting.agenda/responsible])
                                       agenda)}])})

(defcommand :meeting/delete-agenda
  {:doc "Mark given agenda topic as deleted"
   :context {:keys [db user]}
   :payload {agenda-id :agenda-id}
   :project-id (project-db/agenda-project-id db agenda-id)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
           db user
           (meeting-db/agenda-meeting-id db agenda-id))]
   :transact (update-meeting-tx
               (meeting-db/agenda-meeting-id db agenda-id)
               [(meta-model/deletion-tx user agenda-id)])})

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
   :transact (update-meeting-tx
               (get-in (du/entity db id) [:participation/in :db/id])
               [(meta-model/deletion-tx user id)])})

(defcommand :meeting/add-participation
  {:doc "Remove a participation."
   :context {:keys [db user]}
   :payload {meeting :participation/in
             participant :participation/participant :as participation}
   :project-id (project-db/meeting-project-id db meeting)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer? db user meeting)]
   :transact [(list 'teet.meeting.meeting-tx/add-participation
                    (-> participation
                        (select-keys [:participation/in
                                      :participation/role
                                      :participation/participant])
                        (merge {:db/id "new-participation"})
                        (update :participation/participant
                                #(if (string? (:db/id %))
                                   ;; Don't allow adding arbitrary attributes to new users created
                                   ;; via participation
                                   (select-keys % #{:db/id
                                                    :user/given-name
                                                    :user/family-name
                                                    :user/email})
                                   %))))]})

(defn meeting-link [db base-url meeting project-eid]
  (let [{:keys [meeting-eid project-thk-id activity-eid]}
        (project-db/meeting-parents db meeting project-eid)]
    (str base-url
         (if (str/ends-with? base-url "/")
           ""
           "/")
         "#/projects/" project-thk-id
         "/meetings/" activity-eid
         "/" meeting-eid)))


(defn historical-meeting-notify [db meeting from-user participants project-eid]
  (log/debug "from-user is" from-user)
  (tx-ret
   (vec
    (for [to-user participants]
      (notification-db/notification-tx
       db
       (do
         (log/debug "to-user is" to-user)
           
         {:from from-user
          :to to-user
          :target (:db/id meeting)
          :type :notification.type/meeting-updated
          :project project-eid}))))))

(defn email-subject-for-type [meeting type]
  (case type
    :invitation
    (str "TEET: koosoleku kutse: " (meeting-model/meeting-title meeting) " / "
         "TEET: meeting invitation: " (meeting-model/meeting-title meeting))

    :past-update
    (str "TEET: koosolek uuendatud: " (meeting-model/meeting-title meeting) " / "
         "TEET: meeting updated: " (meeting-model/meeting-title meeting))))

(defn email-parts-for-ical-invitation [ics]
  [{:headers {"Content-Type" "text/calendar; method=request"}
    :body ics}])

(defn email-parts-for-past-update [meeting meeting-link]
  [{:headers {"Content-Type" "text/plain; charset=utf-8"}
    :body (str "Koosolek / meeting: " meeting-link "\n\n")}])

(defn send-meeting-email! [db meeting project-eid meeting-link to meeting-eid invitation-or-past-update?]
  (let [meeting-link (meeting-link db
                          (environment/config-value :base-url)
                          meeting project-eid)
            ics (meeting-ics/meeting-ics {:meeting meeting
                                          :meeting-link meeting-link
                                          :cancel? false})
            email-response
            (integration-email/send-email!
              {:from (environment/ssm-param :email :from)
               :to to
               :subject (email-subject-for-type meeting
                                                invitation-or-past-update?)
               :parts (case invitation-or-past-update?
                        :past-update
                        (email-parts-for-past-update meeting meeting-link)
                        :invitation
                        (email-parts-for-ical-invitation ics))
               })]
        (log/info "SES send response" email-response)
        (tx-ret [{:db/id meeting-eid
                  :meeting/invitations-sent-at (Date.)}])))

(defcommand :meeting/send-notifications
  {:doc "Send iCalendar notifications to organizer and participants."
   :context {:keys [db conn user]}
   :payload {meeting-eid :db/id}
   :project-id (project-db/meeting-project-id db meeting-eid)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer? db user meeting-eid)]}
  (let [project-eid (project-db/meeting-project-id db meeting-eid)
        to (remove #(str/ends-with? % "@example.com")
                   (keep :user/email (meeting-db/participants db meeting-eid)))
        meeting (d/pull db
                        '[:db/id
                          :meeting/number
                          :meeting/title :meeting/location
                          :meeting/start :meeting/end
                          {:meeting/organizer [:user/email :user/given-name :user/family-name]}
                          {:meeting/agenda [:meeting.agenda/topic
                                            {:meeting.agenda/responsible [:user/given-name
                                                                          :user/family-name]}]}] meeting-eid)]
    (assert (:db/id user))
    (cond
      (date-in-past? (:meeting/start meeting))
      (do
        (send-meeting-email! db meeting project-eid meeting-link to meeting-eid :past-update)
        (historical-meeting-notify db meeting user
                                   (meeting-db/participants db meeting-eid)
                                   project-eid))

      (not (seq to))
      {:error :no-participants-with-email}

      :else
      (send-meeting-email! db meeting project-eid meeting-link to meeting-eid :invitation))))


(defcommand :meeting/cancel
  {:doc "Delete existing meeting"
   :context {:keys [db user conn]}
   :payload {:keys [activity-eid meeting-id]}
   :project-id (project-db/meeting-project-id db meeting-id)
   :authorization {:activity/edit-activity {}}
   :pre [(meeting-db/activity-meeting-id db activity-eid meeting-id)
         (meeting-db/user-is-organizer-or-reviewer? db user meeting-id)]}
  (let [to (remove #(str/ends-with? % "@example.com")
                   (keep :user/email (meeting-db/participants db meeting-id)))
        tx-return (tx-ret (update-meeting-tx
                            meeting-id
                            [(meta-model/deletion-tx user meeting-id)]))]
    (when (seq to)
      (let [meeting (d/pull db
                            '[:db/id
                              :meeting/number :meeting/invitations-sent-at
                              :meeting/title :meeting/location
                              :meeting/start :meeting/end
                              {:meeting/organizer [:user/email :user/given-name :user/family-name]}
                              {:meeting/agenda [:meeting.agenda/topic
                                                {:meeting.agenda/responsible [:user/given-name
                                                                              :user/family-name]}]}]
                            meeting-id)
            ics (meeting-ics/meeting-ics {:meeting meeting
                                          :cancel? true})
            email-response (when (:meeting/invitations-sent-at meeting) ;; check that for this meeting invitations have been sent
                             (integration-email/send-email!
                               {:from (environment/ssm-param :email :from)
                                :to to
                                :subject (str "TEET: " (meeting-model/meeting-title meeting) " tühistatud" " / "
                                              "TEET: " (meeting-model/meeting-title meeting) " cancelled")
                                :parts [{:headers {"Content-Type" "text/calendar; method=cancel"} ;; RFC 6047 SECTION 2.4
                                         :body ics}]}))]
        (log/info "email response: " (or email-response "no email sent"))))
    tx-return))

(defcommand :meeting/create-decision
  {:doc "Create a new decision under a topic"
   :context {:keys [db user]}
   :payload {:keys [agenda-eid form-data]}
   :project-id (project-db/agenda-project-id db agenda-eid)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
           db user
           (get-in (du/entity db agenda-eid) [:meeting/_agenda :db/id]))]
   :transact (update-meeting-tx
               (meeting-db/agenda-meeting-id db agenda-eid)
               [{:db/id agenda-eid
                 :meeting.agenda/decisions [(merge (select-keys form-data [:meeting.decision/body])
                                                   {:db/id "new decision"
                                                    :meeting.decision/number (meeting-db/next-decision-number db agenda-eid)}
                                                   (meta-model/creation-meta user))]}])})

(defcommand :meeting/update-decision
  {:doc "Create a new decision under a topic"
   :context {:keys [db user]}
   :payload {:keys [form-data]}
   :project-id (project-db/decision-project-id db (:db/id form-data))
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
           db user
           (meeting-db/decision-meeting-id db (:db/id form-data)))]
   :transact (update-meeting-tx
               (meeting-db/decision-meeting-id db (:db/id form-data))
               [(merge (select-keys form-data [:meeting.decision/body :db/id])
                       (meta-model/modification-meta user))])})

(defcommand :meeting/delete-decision
  {:doc "Mark a given decision as deleted"
   :context {:keys [db user]}
   :payload {:keys [decision-id]}
   :project-id (project-db/decision-project-id db decision-id)
   :authorization {}
   :pre [(meeting-db/user-is-organizer-or-reviewer?
           db user
           (meeting-db/decision-meeting-id db decision-id))]
   :transact (update-meeting-tx
               (meeting-db/decision-meeting-id db decision-id)
               [(meta-model/deletion-tx user decision-id)])})

(defcommand :meeting/review
  {:doc "Either approve or reject the meeting"
   :context {:keys [db user]}
   :payload {:keys [meeting-id form-data]}
   :project-id (project-db/meeting-project-id db meeting-id)
   :authorization {}
   :pre [[(meeting-db/user-is-organizer-or-reviewer?
            db user meeting-id)]]
   :transact [(list 'teet.meeting.meeting-tx/review-meeting
                    user
                    meeting-id
                    (select-keys form-data [:review/comment :review/decision]))]})
