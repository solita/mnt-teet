(ns teet.meeting.meeting-db
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.db-api.core :as db-api]
            [teet.util.datomic :as du]
            [teet.meeting.meeting-model :as meeting-model]
            [teet.project.project-db :as project-db]
            [teet.link.link-db :as link-db]
            [clojure.walk :as walk]
            [teet.meta.meta-query :as meta-query]))

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

(defn next-decision-number
  [db agenda-topic-id]
  (or (some-> (d/q '[:find (max ?n)
                     :in $ ?at
                     :where
                     [?at :meeting.agenda/decisions ?d]
                     [(missing? $ ?d :meta/deleted?)]
                     [?d :meeting.decision/number ?n]]
                   db agenda-topic-id)
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
          (d/q '[:find (pull ?u [:user/given-name :user/family-name :db/id
                                 :user/email :user/person-id])
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
  (into #{}
        (map first)
        (d/q '[:find ?u
               :where
               [?p :participation/in ?m]
               [(missing? $ ?p :meta/deleted?)]
               (or
                 [?p :participation/role :participation.role/reviewer]
                 [?p :participation/role :participation.role/organizer])
               (or
                 [(missing? $ ?p :participation/absent?)]
                 [?p :participation/absent? false])
               [?p :participation/participant ?u]
               :in $ ?m]
             db meeting-id)))

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

(defn duplicate-info
  "Fetch meeting info required to duplicate the meeting"
  [db id]
  (d/pull db '[:meeting/title
               :meeting/location
               :meeting/organizer
               {:activity/_meetings [:db/id]}
               {:meeting/agenda [:db/id
                                 :meeting.agenda/topic
                                 :meeting.agenda/body
                                 :meeting.agenda/responsible]}
               {:participation/_in [:meta/deleted? :participation/role
                                    {:participation/participant [:db/id :meta/deleted?]}]}] id))

(defn meeting-organizer-participation
  [db meeting-id]
  (-> (d/q '[:find ?p
             :in $ ?m
             :where
             [?p :participation/in ?m]
             [?p :participation/role :participation.role/organizer]]
           db meeting-id)
      ffirst))

(defn user-can-review?
  "Check that the users participation in the meeting is either reviewer or organizer"
  [db user meeting-id]
  (-> (d/q '[:find ?p
             :in $ ?u ?m
             :where
             [?p :participation/in ?m]
             [?p :participation/participant ?u]
             (or [?p :participation/role :participation.role/organizer]
                 [?p :participation/role :participation.role/reviewer])
             (or [(missing? $ ?p :participation/absent?)]
                 [?p :participation/absent? false])]
           db (:db/id user) meeting-id)
      not-empty
      boolean))


(defn without-incomplete-uploads [tree]
  (let [walker-fn (fn [m]
                    (if (not-empty (:file/_attached-to m))
                      (update m :file/_attached-to #(filterv :file/upload-complete? %))
                      m))]
    (walk/postwalk walker-fn tree)))

(defn export-meeting* [db id]
  (d/pull db '[:db/id
               :meeting/title
               :meeting/location
               :meeting/start
               :meeting/end
               :meeting/number
               {:activity/_meetings [:db/id {:thk.lifecycle/_activities [{:thk.project/_lifecycles [:thk.project/id]}]}]}
               {:meeting/agenda [:db/id
                                 :meeting.agenda/topic
                                 :meeting.agenda/body
                                 {:meeting.agenda/responsible [:user/family-name :user/given-name :user/id]}
                                 {:meeting.agenda/decisions [:db/id :meeting.decision/body :meeting.decision/number
                                                             {:file/_attached-to [:file/name :db/id :file/upload-complete?]}]}
                                 {:file/_attached-to [:file/name :db/id :file/upload-complete?]}]}
               {:review/_of [:meta/created-at :review/comment {:review/decision [:db/id :ident]}
                             {:review/reviewer [:db/id :user/family-name :user/given-name :user/id]}]}
               {:participation/_in [:participation/role
                                    :meta/deleted?
                                    {:participation/participant [:db/id :user/family-name :user/given-name :user/id]}
                                    :participation/absent?]}] id))

(defn export-meeting
  "Fetch information required for export meeting PDF"
  [db user id]
  (link-db/fetch-links
   {:db db
    :user user
    :valid-external-ids-by-type {}
    :fetch-links-pred? #(or (contains? % :meeting.agenda/topic)
                            (contains? % :meeting.decision/body))
    :return-links-to-deleted? true}
   (without-incomplete-uploads
    (meta-query/without-deleted
     db
     (export-meeting* db id)))))
