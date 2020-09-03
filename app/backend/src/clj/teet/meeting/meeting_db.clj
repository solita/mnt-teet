(ns teet.meeting.meeting-db
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.db-api.core :as db-api]
            [teet.util.datomic :as du]))

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
  (let [user-id (:db/id (du/entity db (user-model/user-ref user)))
        {:meeting/keys [organizer participants]}
        (d/pull db '[:meeting/organizer
                     {:meeting/participants
                      [:meeting.participant/user
                       :meeting.participant/role]}] meeting-id)]
    (or (= user-id (:db/id organizer))
        (some #(and (du/enum= :meeting.participant.role/reviewer
                              (:meeting.participant/role %))
                    (= user-id (get-in % [:meeting.participant/user :db/id])))
              participants))))
