(ns teet.participation.participation-model
  (:require [teet.util.datomic :as du]))

(defn user-in-role?
  "Given list of participations, a user and a role, check if user has role.
  Returns true if a participation for the user exists with the given role."
  [participations user role]
  (some #(and (= (:db/id user) (:db/id (:participation/participant %)))
              (du/enum= role (:participation/role %)))
        participations))

(defn user-can-review?
  "Given a list of participations and a user, check if the user can give a review.
  Returns true if the users participation is either organizer or reviewer and is not absent?"
  [participations user]
  (some #(and (= (:db/id user) (:db/id (:participation/participant %)))
              (not (:participation/absent? %))
              (or (du/enum= :participation.role/organizer (:participation/role %))
                  (du/enum= :participation.role/reviewer (:participation/role %))))
        participations))
