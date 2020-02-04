(ns teet.authorization.authorization-check
  (:require #?(:clj  [clojure.java.io :as io]
               :cljs [cljs.reader :as reader])
            #?(:cljs [teet.app-state :as app-state])
            [teet.log :as log]))

(defonce authorization-rules
         (delay #?(:cljs (-> js/window
                             (aget "teet_authz")
                             reader/read-string)
                   :clj  (some-> "authorization.edn"
                                 io/resource
                                 slurp
                                 read-string))))

(defn authorized?
  [user functionality entity]                               ;;TODO check functionality
  (let [creator-id (get-in entity [:meta/creator :db/id])
        user-id (:db/id user)
        rule (@authorization-rules functionality)]
    (= creator-id user-id)))

(defn access-for
  "Returns the description of access given in the `rule` for `role`."
  [rule role]
  (-> @authorization-rules (get rule) (get role)))

(defn user-pm-or-manager?
  [user {:thk.project/keys [manager owner] :as project}]
  (let [owner-id (:user/id owner)
        manager-id (:user/id manager)
        cur-user-id (:user/id user)]
    (or (= cur-user-id owner-id) (= cur-user-id manager-id))))

#?(:clj
   (defn check-authorized
     [user functionality entity]
     (when-not (authorized? user functionality entity)
       (throw (ex-info "Unauthorized" {:user          user
                                       :functionality functionality
                                       :entity        entity})))))

#?(:clj
   (defmacro when-authorized
     [user functionality entity & body]
     `(when (authorized? ~user ~functionality ~entity)
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
         (when (authorized? @app-state/user functionality entity)
               component)))

(defn authorization-rule-names []
  (into #{} (keys @authorization-rules)))
