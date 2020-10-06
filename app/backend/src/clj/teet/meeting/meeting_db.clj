(ns teet.meeting.meeting-db
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.db-api.core :as db-api]
            [teet.util.datomic :as du]
            [teet.meeting.meeting-model :as meeting-model]
            [teet.project.project-db :as project-db]
            [teet.link.link-db :as link-db]))

(defn meetings
  "Fetch a listing of meetings for the given where
  clause and arguments."
  [db where args-map]
  (let [args (vec args-map)
        arg-names (map first args)
        arg-vals (map second args)]
    (apply
     d/q `[:find (~'pull ~'?meeting
                  [:meeting/title
                   :meeting/location
                   :meeting/start
                   :meeting/end
                   {:meeting/organizer ~user-model/user-listing-attributes}
                   :meeting/number])
           :where ~@where
           :in ~'$ ~@arg-names]
     db
     arg-vals)))

(defn meeting-agenda-ids
  "Return set of agenda item db ids for given meeting"
  [db meeting-id]
  (into #{}
        (map :db/id)
        (:meeting/agenda
         (d/pull db [:meeting/agenda] meeting-id))))

(defn activity-meeting-id
  "Check activity has meeting. Returns meeting id."
  [db activity-id meeting-id]
  (or (ffirst (d/q '[:find ?m
                     :where [?activity :activity/meetings ?m]
                     :in $ ?activity ?m]
                   db activity-id meeting-id))
      (db-api/bad-request! "No such meeting in activity.")))


(defn next-meeting-number [db activity-id title]
  (or (some-> (d/q '[:find (max ?n)
                     :where
                     [?activity :activity/meetings ?meeting]
                     [?meeting :meeting/title ?title]
                     [(missing? $ ?meeting :meta/deleted?)]
                     [?meeting :meeting/number ?n]
                     :in $ ?activity ?title]
                   db activity-id title)
              ffirst
              inc)
      1))


(defn user-is-organizer-or-reviewer? [db user meeting-id]
  (meeting-model/user-is-organizer-or-reviewer?
   (du/entity db (user-model/user-ref user))
   (d/pull db '[:meeting/organizer
                {:participation/_in
                 [:participation/participant
                  :participation/role]}] meeting-id)))

(defn participants
  "List user names and emails of meeting participants for
  notification sending. Includes the meeting organizer."
  [db meeting-id]
  (let [{organizer :meeting/organizer}
        (d/pull db '[:meeting/organizer] meeting-id)
        recipients (into #{(:db/id organizer)}
                         (map first)
                         (d/q '[:find ?u
                                :where
                                [?p :participation/in ?m]
                                [(missing? $ ?p :meta/deleted?)]
                                [?p :participation/participant ?u]
                                :in $ ?m] db meeting-id))]
    (mapv first
          (d/q '[:find (pull ?u [:user/given-name :user/family-name
                                 :user/email])
                 :in $ [?u ...]]
               db recipients))))

(defn user-is-participating?
  "Check if given user is participating in the given meeting (in any role)."
  [db participant meeting-id]
  (let [user (user-model/user-ref participant)]
    (boolean
     (or (seq (d/q '[:find ?m
                     :where [?m :meeting/organizer ?u]
                     :in $ ?m ?u] db meeting-id user))

         (seq (d/q '[:find ?p
                     :where
                     [?p :participation/in ?m]
                     [?p :participation/participant ?u]
                     [(missing? $ ?p :meta/deleted?)]
                     :in $ ?m ?u] db meeting-id user))))))

(defn agenda-meeting-id [db meeting-agenda-id]
  (get-in (du/entity db meeting-agenda-id)
          [:meeting/_agenda :db/id]))

(defn decision-meeting-id [db meeting-decision-id]
  (get-in (du/entity db meeting-decision-id)
          [:meeting.agenda/_decisions :meeting/_agenda :db/id]))

(defn reviewer-ids
  [db meeting-id]
  (let [{organizer :meeting/organizer}
        (d/pull db '[:meeting/organizer] meeting-id)]
    (into #{(:db/id organizer)}
          (map first)
          (d/q '[:find ?u
                 :where
                 [?p :participation/in ?m]
                 [(missing? $ ?p :meta/deleted?)]
                 [?p :participation/role :participation.role/reviewer]
                 [?p :participation/participant ?u]
                 :in $ ?m] db meeting-id))))

(defn approved-by-users
  [db meeting-id]
  (let [users (d/q '[:find ?u
                     :where
                     [?r :review/of ?m]
                     [?r :review/decision :review.decision/approved]
                     [?r :review/reviewer ?u]
                     :in $ ?m] db meeting-id)]
    (into #{}
          (map first)
          users)))

(defn review-retractions
  "Returns a list of retraction transactions for all the reviews of the given meeting-id"
  [db meeting-id]
  (->> (d/pull db '[{:review/_of [:db/id]}] meeting-id)
       :review/_of
       (mapv :db/id)
       (mapv (fn [entity-id]
               [:db/retractEntity entity-id]))))

(defn users-review-retractions
  [db user-id meeting-id]
  (->> (d/q '[:find ?r
              :where
              [?r :review/of ?m]
              [?r :review/reviewer ?u]
              :in $ ?m ?u]
            db
            meeting-id
            user-id)
       (mapv first)
       (mapv (fn [entity-id]
               [:db/retractEntity entity-id]))))

(defn will-review-lock-meeting?
  "Check if the given review will lock the meeting being reviewed"
  [db {:review/keys [decision reviewer of]}]
  (if (= decision :review.decision/approved)
    (let [reviewers (reviewer-ids db of)
          approvers (conj (approved-by-users db of) reviewer)]
      (= reviewers approvers))
    false))

(defn locked?
  [db meeting-id]
  (-> (d/pull db '[:meeting/locked?] meeting-id)
      :meeting/locked?
      boolean))

(defn has-decisions?
  "Checks if the given meeting has any decisions made on it"
  [db meeting-id]
  (-> (d/q '[:find ?m
             :where
             [?m :meeting/agenda ?a]
             [?a :meeting.agenda/decisions ?d]
             [(missing? $ ?d :meta/deleted?)]
             :in $ ?m]
           db
           meeting-id)
      seq
      boolean))

(defn link-from->project [db [type id]]
  (case type
    :meeting-agenda (project-db/agenda-project-id db id)
    :meeting-decision (project-db/decision-project-id db id)))

(defn link-from->meeting [db [type id]]
  (case type
    :meeting-agenda (get-in (du/entity db id) [:meeting/_agenda :db/id])
    :meeting-decision (get-in (du/entity db id) [:meeting.agenda/_decisions :meeting/_agenda :db/id])))

(defn allow-link-to-meeting? [db user from]
  (user-is-organizer-or-reviewer? db user (link-from->meeting db from)))

(defmethod link-db/allow-link? [:meeting-agenda :task]
  [db user from _type _to]
  (allow-link-to-meeting? db user from))

(defmethod link-db/allow-link? [:meeting-decision :task]
  [db user from _type _to]
  (allow-link-to-meeting? db user from))

(defmethod link-db/allow-link-delete? [:meeting-agenda :task]
  [db user from _type _to]
  (allow-link-to-meeting? db user from))

(defmethod link-db/allow-link-delete? [:meeting-decision :task]
  [db user from _type _to]
  (allow-link-to-meeting? db user from))
