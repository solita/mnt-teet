(ns teet.authorization.authorization-check
  (:require [teet.log :as log]
            [teet.app-state :as app-state]
            [cljs.reader :as reader]))

(defonce authorization-rules
  (delay (-> js/window
             (aget "teet_authz")
             reader/read-string)))

(defn when-authorized
  [functionality component]
  ;;FIXME: IMPLEMENT CHECK FUNCTIONALITY
  (if authorization-rules
    (do
      (log/info "Authorization-check functionality: " functionality
                "App-state/User" @app-state/user
                "rules for functionality: " (@authorization-rules functionality))
      component)
    (.alert js/window "No authorization matrix loaded")))   ;;This should not happen if build.sh is ran properly
