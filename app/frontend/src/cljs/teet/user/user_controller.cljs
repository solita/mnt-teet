(ns teet.user.user-controller
  "User info and access rights"
  (:require [reagent.core :as r]
            tuck.effect
            [teet.user.user-roles :as user-roles]))

(defonce roles (r/atom #{}))

(defmethod tuck.effect/process-effect :set-user-roles [e! {new-roles :roles}]
  (reset! roles new-roles))

(defn has-role?
  "Check if current logged in user has given role.
  See: teet.user.user-roles/has-role?"
  [role-or-roles]
  (user-roles/has-role? {:user/roles @roles} role-or-roles))


(defn when-role [role component]
  (when (has-role? role)
    component))
