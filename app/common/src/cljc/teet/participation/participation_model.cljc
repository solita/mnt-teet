(ns teet.participation.participation-model
  (:require [teet.util.datomic :as du]))

(defn user-in-role?
  "Given list of participations, a user and a role, check if user has role.
  Returns true if a participation for the user exists with the given role."
  [participations user role]
  (some #(and (= (:db/id user) (:db/id (:participation/participant %)))
              (du/enum= role (:participation/role %)))
        participations))
