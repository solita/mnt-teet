(ns teet.authorization.authorization-check
  (:require #?(:clj  [clojure.java.io :as io]
               :cljs [cljs.reader :as reader])
            #?(:cljs [teet.app-state :as app-state])
            [teet.log :as log]
            [teet.util.collection :as cu]
            [clojure.set :as set]))

(defonce authorization-rules
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
  ungrantable-roles #{:admin :guest :authenticated-guest})

(defn role-can-be-granted? [role]
  (not (ungrantable-roles role)))

(defn access-for
  "Returns the description of access given in the `rule` for `role`."
  [rule role]
  (-> @authorization-rules (get rule) (get role)))

(defn authorized?
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
                    (= (get-in entity [link :db/id])
                       (:db/id user)))))))
         (:user/permissions user))))



(defn user-pm-or-manager?
  [user {:thk.project/keys [manager owner] :as project}]
  (let [owner-id (:user/id owner)
        manager-id (:user/id manager)
        cur-user-id (:user/id user)]
    (or (= cur-user-id owner-id) (= cur-user-id manager-id))))

#?(:clj
   (defn check-authorized
     [user functionality entity]
     (when-not (authorized? user functionality {:entity entity})
       (throw (ex-info "Unauthorized" {:user          user
                                       :functionality functionality
                                       :entity        entity})))))

#?(:clj
   (defmacro when-authorized
     [user functionality entity & body]
     `(when (authorized? ~user ~functionality {:entity ~entity})
        ~@body)))

#?(:cljs
   (defn when-pm-or-owner
         [project component]
         (log/info "Checking for user to be pm or owner of the project")   ;; TODO: REMOVE THIS WHOLE FN WHEN PROPER IMPLEMENTATION IS DONE FOR AUTHORIZATION CHECK
         (when (user-pm-or-manager? @app-state/user project)
               component)))

#?(:cljs
   (defn when-authorized
         [functionality entity component]
         (log/info "Authorization-check functionality: " functionality
                   "entity : " entity
                   "App-state/User" @app-state/user
                   "rules for functionality: " (@authorization-rules functionality))
     (when (authorized? @app-state/user functionality {:entity entity})
       component)))

(defn authorization-rule-names []
  (into #{} (keys @authorization-rules)))
