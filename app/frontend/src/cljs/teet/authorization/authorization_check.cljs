(ns teet.authorization.authorization-check
  (:require [teet.log :as log]
            [teet.app-state :as app-state]
            [cljs.reader :as reader]))

(defonce authorization-rules
  (delay (-> js/window
             (aget "teet_authz")
             reader/read-string)))

(defn when-authorized
  [functionality entity component]
  ;;FIXME: IMPLEMENT CHECK FUNCTIONALITY || NOW ONLY CHECKS THAT THE USE IS CREATOR OF GIVEN ENTITY
  (log/info "Authorization-check functionality: " functionality
            "entity : " entity
            "App-state/User" @app-state/user
            "rules for functionality: " (@authorization-rules functionality))
  (let [creator-id (get-in entity [:meta/creator :db/id])
        user-id (:db/id @app-state/user)]
    (when (= creator-id user-id)
      component)))
