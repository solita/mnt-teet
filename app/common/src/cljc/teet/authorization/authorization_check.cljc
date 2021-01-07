(ns teet.authorization.authorization-check
  (:require #?(:clj  [clojure.java.io :as io]
               :cljs [cljs.reader :as reader])
            #?(:cljs [teet.app-state :as app-state])
            #?(:cljs [teet.ui.project-context :as project-context])
            [teet.util.collection :as cu]
            [clojure.set :as set]
            [teet.log :as log]))

(def authorization-rules
  (delay #?(:cljs (-> js/window
                      (aget "teet_authz")
                      reader/read-string)
            :clj  (some-> "authorization.edn"
                          io/resource
                          slurp
                          read-string))))

(defonce
  ^{:doc "Delayed set of all role names"}
  all-roles
  (delay (->> @authorization-rules
              vals
              (map (comp set keys))
              (reduce set/union #{}))))

(def ^{:doc "Roles that cannot be currently granted from user interface."
       :private true}
  ungrantable-roles #{:admin :guest :authenticated-guest :manager})

(defn role-can-be-granted? [role]
  (not (ungrantable-roles role)))

(defn access-for
  "Returns the description of access given in the `rule` for `role`."
  [rule role]
  (-> @authorization-rules (get rule) (get role)))

(defmulti check-user-link (fn [_user _entity link] link))

(defmethod check-user-link :default [user entity link]
  (log/debug "checking for link access for user " (:db/id user)
             " under key " link
             " - id " (get-in entity [link :db/id])
             " - match? " (= (get-in entity [link :db/id]) (:db/id user)))
  (= (get-in entity [link :db/id])
     (:db/id user)))

(defn authorized?
  #?(:cljs
     ([{:keys [user functionality] :as opts}]
      (authorized? (or user @app-state/user)
                   functionality
                   opts)))
  ([user functionality]
   (authorized? user functionality nil))
  ([user functionality {:keys [access entity project-id link]
                        :or {link :meta/creator}}]
   (some (fn [{:permission/keys [role projects]}]
           (let [required-access (or access :full)
                 access-for-role
                 (and (if (seq projects)
                        ;; Project specific permission: check project id is included
                        (and project-id
                             (cu/contains-value? projects {:db/id project-id}))

                        ;; Global permission
                        true)

                      (access-for functionality role))]
             (and
              access-for-role
              (or
               ;; full access is required and this permission gives it
               (= required-access access-for-role :full)

               ;; read access required and permission gives full or read
               (and (= required-access :read)
                    (or (= access-for-role :read)
                        (= access-for-role :full)))

               ;; link access required check ownership
               (and (= access-for-role :link) user entity
                    (check-user-link user entity link))))))
         (:user/permissions user))))

#?(:clj
   (defn check-authorized
     [user functionality entity]
     (when-not (authorized? user functionality {:entity entity})
       (throw (ex-info "Unauthorized" {:user          user
                                       :functionality functionality
                                       :entity        entity})))))

#?(:cljs (defonce test-authorize (atom nil)))
#?(:cljs
   (defn when-authorized
     [action entity component]
     (if-let [authorize @test-authorize]
       (when (authorize action entity)
         component)
       [project-context/consume
         (fn [{project-id :db/id}]
          (let [permissions @app-state/action-permissions
                user @app-state/user
                action-permissions (action permissions)]
            (if (and permissions user action-permissions)
              (if (every? (fn [[permission {:keys [link]}]]
                            (authorized? @app-state/user permission
                                         (merge {:project-id project-id
                                                 :entity entity}
                                                (when link {:link link}))))
                          (action permissions))
                (do
                  (log/debug "Action " action " authorized for " user)
                  component)
                (do
                  (log/debug "Action " action " NOT authorized for " user)
                  nil))

              (do
                (log/debug "Can't determine permissions to check authorization"
                           ", action: " action
                           ", action-permissions: " action-permissions
                           ", user: " user)
                nil))))])))

#?(:cljs
   (defn with-authorization-check
     "Call component (hiccup vector) with the result of an authorization
check as the last argument (true/false)."
     [functionality entity component]
     [project-context/consume
      (fn [{project-id :db/id}]
        (let [user @app-state/user]
          (conj component
                (boolean
                 (and user
                      (authorized? user functionality
                                   (merge {:project-id project-id
                                           :entity entity})))))))]))
(defn authorization-rule-names []
  (into #{} (keys @authorization-rules)))
