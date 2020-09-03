(ns teet.meeting.meeting-model
  (:require [teet.util.datomic :as du]))

(defn meeting-title
  [{:meeting/keys [title number]}]
  (str title (when number
               (str " " "#" number))))

(defn user-is-organizer-or-reviewer? [user meeting]
  (let [user-id (:db/id user)
        {:meeting/keys [organizer participants]} meeting]
    (println "User id: " user-id ", org: " organizer ", participants: " participants)
    (or (= user-id (:db/id organizer))
        (some #(and (du/enum= :meeting.participant.role/reviewer
                              (:meeting.participant/role %))
                    (= user-id (get-in % [:meeting.participant/user :db/id])))
              participants))))
