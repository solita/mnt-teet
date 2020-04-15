(ns teet.authorization.authorization-check
  (:require #?(:clj  [clojure.java.io :as io]
               :cljs [cljs.reader :as reader])
            #?(:cljs [teet.app-state :as app-state])
            #?(:cljs [teet.ui.project-context :as project-context])
            #?(:cljs [teet.ui.query :as query])
            [teet.util.collection :as cu]
            [clojure.set :as set]))

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

#?(:clj
   (defn check-authorized
     [user functionality entity]
     (when-not (authorized? user functionality {:entity entity})
       (throw (ex-info "Unauthorized" {:user          user
                                       :functionality functionality
                                       :entity        entity})))))

#?(:cljs
   (defn query-request-permissions! [e!]
     (e! (query/->Query :authorization/permissions
                        {}
                        [:authorization/permissions]
                        nil))))

#?(:cljs
   (defn when-authorized
     [action entity component]
     [project-context/consume
      (fn [{:keys [project-id]}]
        (let [permissions @app-state/action-permissions
              user @app-state/user
              action-permissions (action permissions)]
          (when (and permissions
                     user
                     action-permissions)
            (when (every? (fn [[permission {:keys [link]}]]
                            (authorized? @app-state/user permission (merge {:project-id project-id
                                                                            :entity entity}

                                                                           (when link {:link link}))))
                          (action permissions))
              component))))]))

(defn authorization-rule-names []
  (into #{} (keys @authorization-rules)))
