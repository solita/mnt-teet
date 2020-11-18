(ns teet.meeting.meeting-model
  (:require [teet.participation.participation-model :as participation-model]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.log :as log]))

(defn meeting-title
  [{:meeting/keys [title number]}]
  (str title (when number
               (str " " "#" number))))

(defn user-is-organizer-or-reviewer? [user meeting]
  (let [{participations :participation/_in} meeting]
    (or (participation-model/user-in-role? participations user :participation.role/organizer)
        (participation-model/user-in-role? participations user :participation.role/reviewer))))

(defn user-can-review? [user meeting]
  (let [{participations :participation/_in} meeting]
    (participation-model/user-can-review? participations user)))

(def order {:participation.role/organizer 1
            :participation.role/reviewer 2
            :participation.role/participant 3})

(def role-order
  (comp order #(get-in % [:participation/role :db/ident])))

(def role-id-name
  (juxt role-order
        #(not (get-in % [:participation/participant :user/id]))
        #(get-in % [:participation/participant :user/given-name])
        #(get-in % [:participation/participant :user/family-name])))

(defmethod authorization-check/check-user-link :meeting/organizer-or-reviewer [user meeting _link]
  (log/info "user is meeting org or reviewer"
            ", user:  " user
            ", meeting: " meeting)
  (or (= (get-in meeting [:meeting/organizer :db/id])
         (:db/id user))
      (some #(and (= :participation.role/reviewer
                     (get-in % [:participation/role :db/ident]))
                  (= (:db/id user)
                     (get-in % [:participation/participant :db/id])))
            (:participation/_in meeting))))
