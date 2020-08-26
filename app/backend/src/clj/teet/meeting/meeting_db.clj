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

(defn activity-meeting-id
  "Check activity has meeting. Returns meeting id."
  [db activity-id meeting-id]
  (or (ffirst (d/q '[:find ?m
                     :where [?activity :activity/meetings ?m]
                     :in $ ?activity]
                   db activity-id))
      (db-api/bad-request! "No such meeting in activity.")))
