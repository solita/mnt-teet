(ns teet.user.user-controller
  "User info and access rights"
  (:require [reagent.core :as r]
            tuck.effect))


(defonce roles (r/atom #{}))

(defmethod tuck.effect/process-effect :set-user-roles [e! {new-roles :roles}]
  (reset! roles new-roles))

(defn has-role?
  "Returns true if user has the given role.
  If the input is a single role keyword, checks that the user has that role.
  If the input is a collection of role keywords, checks that user has at least
  one of the roles."
  [role-or-roles]
  (if (keyword? role-or-roles)
    (boolean (@roles role-or-roles))
    (some has-role? role-or-roles)))
