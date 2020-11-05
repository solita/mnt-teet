(ns teet.meeting.meeting-model
  (:require [teet.participation.participation-model :as participation-model]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.log :as log]))

(defn meeting-title
  [{:meeting/keys [title number]}]
  (str title (when number
               (str " " "#" number))))

(defn user-is-organizer-or-reviewer? [user meeting]
  (let [user-id (:db/id user)
        {organizer :meeting/organizer
         participations :participation/_in} meeting]
    (println "User id: " user-id ", org: " organizer ", participations: " participations)
    (or (= user-id (:db/id organizer))
        (participation-model/user-in-role? participations user :participation.role/reviewer))))


(def order {:participation.role/reviewer 1
            :participation.role/participant 2})

(def role-order
  (comp order #(get-in % [:participation/role :db/ident])))

(def role-id-name
  (juxt role-order
        #(not (get-in % [:participation/participant :user/id]))
        #(get-in % [:participation/participant :user/given-name])
        #(get-in % [:participation/participant :user/family-name])))

(defmethod authorization-check/check-user-link :meeting/organizer-or-reviewer [user entity _link]
  (log/info "user is meeting org or reviewer, user:  " user ", entity: "
            entity)
  ;; (meeting-db/user-is-organizer-or-reviewer? db user (:db/id form-data))
  (or (= (get-in entity [:meeting/organizer :db/id])
         (:db/id user))
      (some #(and (= :participation.role/reviewer
                     (get-in % [:participation/role :db/ident])) ())))
  true)
