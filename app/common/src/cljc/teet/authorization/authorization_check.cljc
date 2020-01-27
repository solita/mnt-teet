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

#?(:clj
   (defn check-authorized
     [user functionality entity]
     (when-not (authorized? user functionality entity)
       (throw (ex-info "Unauthorized" {:user user
                                       :functionality functionality
                                       :entity entity})))))

#?(:clj
   (defmacro when-authorized
     [user functionality entity & body]
     `(when (authorized? ~user ~functionality ~entity)
        ~@body)))

#?(:cljs
   (defn when-authorized
         [functionality entity component]
         (log/info "Authorization-check functionality: " functionality
                   "entity : " entity
                   "App-state/User" @app-state/user
                   "rules for functionality: " (@authorization-rules functionality))
         (when (authorized? @app-state/user functionality entity)
               component)))
