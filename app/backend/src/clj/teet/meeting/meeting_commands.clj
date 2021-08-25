(ns teet.meeting.meeting-commands
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defcommand tx-ret]]
            [teet.meta.meta-model :as meta-model]
            [teet.file.file-db :as file-db]
            [teet.project.project-db :as project-db]
            teet.meeting.meeting-specs
            [teet.util.datomic :as du]
            [teet.meeting.meeting-db :as meeting-db]
            [teet.integration.integration-email :as integration-email]
            [teet.environment :as environment]
            [teet.meeting.meeting-model :as meeting-model]
            [teet.log :as log]
            [clojure.string :as str]
            [teet.link.link-db :as link-db]
            [teet.notification.notification-db :as notification-db]
            [teet.util.collection :as cu]
            [teet.localization :refer [tr with-language]]
            [teet.entity.entity-db :as entity-db]
            [clojure.spec.alpha :as s]
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.authorization.authorization-core :refer [special-authorization] :as authorization])
  (:import (java.util Date)))

(defn update-meeting-tx
  [user meeting-id tx-data]
  [(list 'teet.meeting.meeting-tx/update-meeting
         user
         meeting-id
         tx-data)])

(defn- edit-authorized? [db user meeting-id]
  (or (meeting-db/user-is-organizer-or-reviewer? db user meeting-id)
      (authorization/authorized-for-action? {:action :meeting/update
                                             :user user
                                             :db db})
      (authorization-check/authorized?
       user :meeting/edit-meeting
       {:entity (du/entity db meeting-id)
        :link :meeting/organizer-or-reviewer
        :project-id (project-db/meeting-project-id db meeting-id)})))

(defmethod file-db/attach-to :meeting-agenda
  [db user _file [_ meeting-agenda-id]]
  (let [meeting-id (meeting-db/agenda-meeting-id db meeting-agenda-id)]
    (when (edit-authorized? db user meeting-id)
      {:eid meeting-agenda-id
       :wrap-tx #(update-meeting-tx user meeting-id %)})))

(defmethod file-db/allow-download-attachments? :meeting-agenda
  [db user [_ meeting-agenda-id]]
  (or (meeting-db/user-is-participating?
        db user (meeting-db/agenda-meeting-id db meeting-agenda-id))
      (authorization/general-project-access? db user (project-db/agenda-project-id db meeting-agenda-id))))

(defmethod file-db/delete-attachment :meeting-agenda
  [db user file-id [_ meeting-agenda-id]]
  (let [meeting-id (meeting-db/agenda-meeting-id db meeting-agenda-id)]
    (if (edit-authorized? db user meeting-id)
      (update-meeting-tx user meeting-id [(meta-model/deletion-tx user file-id)])
      (throw (ex-info "Unauthorized attachment delete"
                      {:error :unauthorized})))))

(defmethod file-db/attach-to :meeting-decision
  [db user _file [_ meeting-decision-id]]
  (let [meeting-id (meeting-db/decision-meeting-id db meeting-decision-id)]
    (when (edit-authorized? db user meeting-id)
      {:eid meeting-decision-id
       :wrap-tx #(update-meeting-tx user meeting-id %)})))

(defmethod file-db/allow-download-attachments? :meeting-decision
  [db user [_ meeting-decision-id]]
  (or (meeting-db/user-is-participating?
        db user
        (meeting-db/decision-meeting-id db meeting-decision-id))
      (authorization/general-project-access? db user (project-db/decision-project-id db meeting-decision-id))))

(defmethod file-db/delete-attachment :meeting-decision
  [db user file-id [_ meeting-decision-id]]
  (let [meeting-id (meeting-db/decision-meeting-id db meeting-decision-id)]
    (if (edit-authorized? db user meeting-id)
      (update-meeting-tx user meeting-id
                         [(meta-model/deletion-tx user file-id)])
      (throw (ex-info "Unauthorized attachment delete"
                      {:error :unauthorized})))))


(defn- link-from-meeting [db user from]
  (let [meeting-id (meeting-db/link-from->meeting db from)]
    (when (edit-authorized? db user meeting-id)
      {:wrap-tx #(update-meeting-tx user meeting-id %)})))

(def link-types [[:meeting-agenda :task]
                 [:meeting-decision :task]
                 [:meeting-agenda :cadastral-unit]
                 [:meeting-agenda :estate]
                 [:meeting-decision :cadastral-unit]
                 [:meeting-decision :estate]
                 [:meeting-agenda :file]
                 [:meeting-decision :file]])

(doseq [link-type link-types]
  (defmethod link-db/link-from link-type
    [db user from _type _to]
    (link-from-meeting db user from))
  (defmethod link-db/delete-link-from link-type
    [db user from _type _to]
    (link-from-meeting db user from)))



(defcommand :meeting/create
  {:doc "Create new meetings to activities"
   :context {:keys [db user]}
   :payload {:keys [activity-eid]
             :meeting/keys [form-data]}
   :project-id (project-db/activity-project-id db activity-eid)
   :transact [(list 'teet.meeting.meeting-tx/create-meeting
                    activity-eid
                    (merge (select-keys form-data [:meeting/title :meeting/location
                                                   :meeting/start :meeting/end
                                                   :meeting/organizer])
                           (meta-model/creation-meta user)))]})

(defmethod special-authorization :meeting/update
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/update
  {:doc "Update existing meeting"
   :context {:keys [db user]}
   :payload {:keys [activity-eid]
             :meeting/keys [form-data]}
   :project-id (project-db/activity-project-id db activity-eid)
   :authorization {:meeting/edit-meeting {:db/id (:db/id form-data)
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/update
                            :entity-id (:db/id form-data)}
   :pre [(meeting-db/activity-meeting-id db activity-eid (:db/id form-data))]
   :transact (update-meeting-tx
               user
               (:db/id form-data)
               (let [{old-meeting-title :meeting/title
                      old-organizer :meeting/organizer}
                     (d/pull db '[:meeting/title :meeting/organizer]
                             (:db/id form-data))
                     new-organizer (get-in form-data [:meeting/organizer])
                     meeting-organizer-participation (meeting-db/meeting-organizer-participation db (:db/id form-data))]

                 ;; New organizer must not already be a participant
                 ;; PENDING: these could be done in the update-meeting-tx db fn
                 (when (and (not= (:db/id old-organizer)
                                  (:db/id new-organizer))
                            (meeting-db/user-is-participating? db new-organizer (:db/id form-data)))
                   (db-api/fail! {:error :user-is-already-participant}))

                 [(merge
                    (select-keys form-data [:db/id :meeting/organizer :meeting/title
                                            :meeting/start :meeting/end :meeting/location])
                    (when (not= old-meeting-title (:meeting/title form-data))
                      ;; Changing meeting title, we need to renumber the meeting
                      {:meeting/number (meeting-db/next-meeting-number
                                         db activity-eid (:meeting/title form-data))}))
                  ;; Change the meetings organizer participation to the new organizer
                  {:db/id meeting-organizer-participation
                   :participation/participant (:db/id new-organizer)}]))})

(defn- agenda-items-new-or-belong-to-meeting [db meeting-id agenda]
  (let [ids-to-update (remove string? (map :db/id agenda))
        existing-ids (meeting-db/meeting-agenda-ids db meeting-id)]
    (every? existing-ids ids-to-update)))

(defmethod special-authorization :meeting/update-agenda
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/update-agenda
  {:doc "Add/update agenda item(s) in a meeting"
   :context {:keys [db user]}
   :spec (s/keys :req [:db/id :meeting/agenda])
   :payload {meeting-id :db/id
             agenda :meeting/agenda}
   :project-id (project-db/meeting-project-id db meeting-id)
   :authorization {:meeting/edit-meeting {:db/id meeting-id
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/update-agenda
                            :entity-id meeting-id}
   :pre [(agenda-items-new-or-belong-to-meeting db meeting-id agenda)]
   :transact
   (db-api-large-text/store-large-text!
    meeting-model/rich-text-fields
    (update-meeting-tx
     user
     meeting-id
     [{:db/id meeting-id
       :meeting/agenda
       (mapv #(meta-model/with-creation-or-modification-meta
                user
                (select-keys % [:db/id
                                :meeting.agenda/topic
                                :meeting.agenda/body
                                :meeting.agenda/responsible]))
             agenda)}]))})

(defmethod special-authorization :meeting/delete-agenda
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/delete-agenda
  {:doc "Mark given agenda topic as deleted"
   :context {:keys [db user]}
   :payload {agenda-id :agenda-id}
   :project-id (project-db/agenda-project-id db agenda-id)
   :authorization {:meeting/edit-meeting {:db/id (meeting-db/agenda-meeting-id db agenda-id)
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/delete-agenda
                            :entity-id (meeting-db/agenda-meeting-id db agenda-id)}
   :transact (update-meeting-tx
               user
               (meeting-db/agenda-meeting-id db agenda-id)
               [(meta-model/deletion-tx user agenda-id)])})

(defmethod special-authorization :meeting/remove-participation
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/remove-participation
  {:doc "Remove a participation."
   :context {:keys [db user]}
   :payload {id :db/id}
   :project-id (project-db/meeting-project-id
                 db
                 (get-in (du/entity db id) [:participation/in :db/id]))
   :authorization {:meeting/edit-meeting {:db/id (get-in (du/entity db id) [:participation/in :db/id])
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/remove-participation
                            :entity-id (get-in (du/entity db id) [:participation/in :db/id])}
   :transact (update-meeting-tx
               user
               (get-in (du/entity db id) [:participation/in :db/id])
               [(meta-model/deletion-tx user id)])})

(defmethod special-authorization :meeting/add-participation
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/add-participation
  {:doc "Remove a participation."
   :context {:keys [db user]}
   :payload {meeting :participation/in
             participant :participation/participant :as participation}
   :project-id (project-db/meeting-project-id db meeting)
   :authorization {:meeting/edit-meeting {:db/id meeting
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/add-participation
                            :entity-id meeting}
   :transact [(list 'teet.meeting.meeting-tx/add-participation
                    user
                    (-> participation
                        (select-keys [:participation/in
                                      :participation/role
                                      :participation/participant])
                        (merge {:db/id "new-participation"})
                        (merge (meta-model/creation-meta user))
                        (update :participation/participant
                                #(if (string? (:db/id %))
                                   ;; Don't allow adding arbitrary attributes to new users created
                                   ;; via participation
                                   (select-keys % #{:db/id
                                                    :user/given-name
                                                    :user/family-name
                                                    :user/email})
                                   %))))]})

(defmethod special-authorization :meeting/change-participation-absence
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/change-participation-absence
  {:doc "Change the status of participation to absent or not absent"
   :context {:keys [db user]}
   :payload {participation-id :participation-id
             absent? :absent?}
   :project-id (project-db/meeting-project-id
                 db
                 (get-in (du/entity db participation-id) [:participation/in :db/id]))
   :authorization {:meeting/edit-meeting {:db/id (get-in (du/entity db participation-id) [:participation/in :db/id])
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/change-participation-absence
                            :entity-id (get-in (du/entity db participation-id) [:participation/in :db/id])}
   :transact (update-meeting-tx
               user
               (get-in (du/entity db participation-id) [:participation/in :db/id])
               [{:db/id participation-id
                 :participation/absent? absent?}])})

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


(defn notify-about-meeting [db meeting from-user participants project-eid]
  (log/debug "from-user is" from-user)
  (tx-ret
   (vec
    (for [to-user participants
          :when (:user/person-id to-user)]
      (notification-db/notification-tx
       db
       (do
         (log/debug "to-user is" to-user)

         {:from from-user
          :to to-user
          :target (:db/id meeting)
          :type :notification.type/meeting-updated
          :project project-eid}))))))

(defn email-subject [meeting]
  (str (with-language :et (tr [:meeting :email-title] {:meeting-title (meeting-model/meeting-title meeting)}))
       " / "
       (with-language :en (tr [:meeting :email-title] {:meeting-title (meeting-model/meeting-title meeting)}))))

(defn email-content [link]
  (str (with-language :et (tr [:meeting :email-body]))
    " / "
    (with-language :en (tr [:meeting :email-body])) ": " link "\n\n"))

(defn email-parts [meeting-link]
  [{:type "text/plain; charset=utf-8"
    :content (email-content meeting-link)}])

(defn send-meeting-email! [db meeting project-eid meeting-link to meeting-eid]
  (let [meeting-link (meeting-link db
                                   (environment/config-value :base-url)
                                   meeting project-eid)
        email-response
        (integration-email/send-email!
          {:from (environment/config-value :email :from)
           :to to
           :subject (email-subject meeting)
           :body (email-parts meeting-link)})]
    (log/info "SES send response" email-response)
    (log/info "SES emails sent to: " (pr-str to))
    (tx-ret [{:db/id meeting-eid
              :meeting/notifications-sent-at (Date.)}])))

(defmethod special-authorization :meeting/send-notifications
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/send-notifications
  {:doc "Send email / in-app notifications to organizer and participants."
   :context {:keys [db conn user]}
   :payload {meeting-eid :db/id}
   :project-id (project-db/meeting-project-id db meeting-eid)
   :authorization {:meeting/send-notifications {:db/id meeting-eid
                                                :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/send-notifications
                            :entity-id meeting-eid}}
  (let [project-eid (project-db/meeting-project-id db meeting-eid)
        all-to (meeting-db/participants db meeting-eid)
        to (->> (remove #(= (:db/id user) (:db/id %)) all-to) ;; don't send emails to current user
                (keep :user/email)
                (remove (fn [participant]
                          (str/ends-with? participant "@example.com"))))
        meeting (d/pull db
                        '[:db/id
                          :meeting/number
                          :meeting/title :meeting/location
                          :meeting/start :meeting/end
                          {:meeting/organizer [:user/email :user/given-name :user/family-name]}
                          {:meeting/agenda [:meeting.agenda/topic
                                            {:meeting.agenda/responsible [:user/given-name
                                                                          :user/family-name]}]}]
                        meeting-eid)]
    (assert (:db/id user))
    (log/info "Trying to send emails for a meeting with id: " meeting-eid)
    (log/info "Count of all participants: " (count all-to) " Count of participants with email addresses: " (count to))
    (if (not (seq to))
      (do
        (log/warn "not sending email because no participants with email")
        (throw (ex-info "no participants with emails"
                        {:error :no-participants-with-email})))
      (do
        (log/debug "send-meeting-email in future mode because meeting in future")
        (notify-about-meeting db meeting user
                              all-to
                              project-eid)
        (send-meeting-email! db meeting project-eid meeting-link to meeting-eid)))))

(defmethod special-authorization :meeting/cancel
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/cancel
  {:doc "Delete existing meeting"
   :context {:keys [db user conn]}
   :payload {:keys [activity-eid meeting-id]}
   :project-id (project-db/meeting-project-id db meeting-id)
   :authorization {:meeting/edit-meeting {:db/id meeting-id
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/cancel
                            :entity-id meeting-id}
   :pre [(meeting-db/activity-meeting-id db activity-eid meeting-id)]}
  (tx-ret (update-meeting-tx
            user
            meeting-id
            ;; Can't use deletion-tx because update-meeting-tx also adds modification timestamp to the tx
            [{:db/id meeting-id
              :meta/deleted? true}])))

(defmethod special-authorization :meeting/create-decision
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/create-decision
  {:doc "Create a new decision under a topic"
   :context {:keys [db user]}
   :payload {:keys [agenda-eid form-data]}
   :project-id (project-db/agenda-project-id db agenda-eid)
   :contract-authorization {:action :meeting/create-decision
                            :entity-id (get-in (du/entity db agenda-eid)
                                               [:meeting/_agenda :db/id])}
   :authorization {:meeting/edit-meeting {:db/id (get-in (du/entity db agenda-eid)
                                                         [:meeting/_agenda :db/id])
                                          :link :meeting/organizer-or-reviewer}}

   :transact
   (db-api-large-text/store-large-text!
    meeting-model/rich-text-fields
    (update-meeting-tx
     user
     (meeting-db/agenda-meeting-id db agenda-eid)
     [{:db/id agenda-eid
       :meeting.agenda/decisions [(merge (select-keys form-data [:meeting.decision/body])
                                         {:db/id "new decision"
                                          :meeting.decision/number (meeting-db/next-decision-number db agenda-eid)}
                                         (meta-model/creation-meta user))]}]))})

(defmethod special-authorization :meeting/update-decision
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/update-decision
  {:doc "Create a new decision under a topic"
   :context {:keys [db user]}
   :payload {:keys [form-data]}
   :project-id (project-db/decision-project-id db (:db/id form-data))
   :contract-authorization {:action :meeting/update-decision
                            :entity-id (meeting-db/decision-meeting-id db (:db/id form-data))}
   :authorization {:meeting/edit-meeting {:db/id (meeting-db/decision-meeting-id db (:db/id form-data))
                                          :link :meeting/organizer-or-reviewer}}
   :transact
   (db-api-large-text/store-large-text!
    meeting-model/rich-text-fields
    (update-meeting-tx
     user
     (meeting-db/decision-meeting-id db (:db/id form-data))
     [(merge (select-keys form-data [:meeting.decision/body :db/id])
             (meta-model/modification-meta user))]))})

(defmethod special-authorization :meeting/delete-decision
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/delete-decision
  {:doc "Mark a given decision as deleted"
   :context {:keys [db user]}
   :payload {:keys [decision-id]}
   :project-id (project-db/decision-project-id db decision-id)
   :authorization {:meeting/edit-meeting {:db/id (meeting-db/decision-meeting-id db decision-id)
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/delete-decision
                            :entity-id (meeting-db/decision-meeting-id db decision-id)}
   :transact (update-meeting-tx
               user
               (meeting-db/decision-meeting-id db decision-id)
               [(meta-model/deletion-tx user decision-id)])})

(defmethod special-authorization :meeting/review
  [{:keys [db user entity-id]}]
  (meeting-db/user-is-organizer-or-reviewer? db user entity-id))

(defcommand :meeting/review
  {:doc "Either approve or reject the meeting"
   :context {:keys [db user]}
   :payload {:keys [meeting-id form-data]}
   :project-id (project-db/meeting-project-id db meeting-id)
   :authorization {:meeting/edit-meeting {:db/id meeting-id
                                          :link :meeting/organizer-or-reviewer}}
   :contract-authorization {:action :meeting/review
                            :entity-id meeting-id}
   :pre [(meeting-db/user-can-review? db user meeting-id)]
   :transact [(list 'teet.meeting.meeting-tx/review-meeting
                    user
                    meeting-id
                    (select-keys form-data [:review/comment :review/decision]))]})

(defcommand :meeting/duplicate
  {:doc "Duplicate this meeting into a new instance"
   :context {:keys [db user]}
   :payload {id :db/id
             :meeting/keys [title location organizer start end]}
   :project-id (project-db/meeting-project-id db id)
   :transact
   (let [{:meeting/keys [agenda] :as old-meeting}
         (meeting-db/duplicate-info db id)]
     (into
       [(list 'teet.meeting.meeting-tx/create-meeting
              (get-in old-meeting [:activity/_meetings 0 :db/id])
              (merge (select-keys old-meeting [:meeting/title :meeting/location :meeting/organizer])
                     (cu/without-nils
                       {:db/id "new-meeting"
                        :meeting/title title
                        :meeting/location location
                        :meeting/organizer organizer
                        :meeting/start start
                        :meeting/end end})
                     (when (seq agenda)
                       {:meeting/agenda (vec (for [a agenda]
                                               (merge
                                                 {:db/id (str "new-agenda-" (:db/id a))}
                                                 (select-keys a [:meeting.agenda/topic
                                                                 :meeting.agenda/body
                                                                 :meeting.agenda/responsible]))))})
                     (meta-model/creation-meta user)))]
       (for [{:participation/keys [role participant]
              deleted? :meta/deleted?} (:participation/_in old-meeting)
             ;; Do not duplicate organizer participation
             :when (and (not deleted?)
                        (not (:meta/deleted? participant))
                        (not (du/enum= role :participation.role/organizer)))]
         {:db/id (str "new-participation-" (:db/id participant))
          :participation/in "new-meeting"
          :participation/role (:db/ident role)
          :participation/participant (:db/id participant)})))})


(defcommand :meeting/seen
  {:doc "Mark meeting seen timestamp for user"
   :payload {id :db/id}
   :spec (s/keys :req [:db/id])
   :context {:keys [db user]}
   :project-id (project-db/meeting-project-id db id)
   :transact [(entity-db/entity-seen-tx db user id)]})
