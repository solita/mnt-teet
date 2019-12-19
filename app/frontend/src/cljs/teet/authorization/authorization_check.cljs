(ns teet.authorization.authorization-check
  (:require [teet.log :as log]
            [teet.app-state :as app-state]))

(defn when-authorized
  [functionality component]                                 ;;FIXME: IMPLEMENT CHECK FUNCTIONALITY
  (log/info "Authorization-check functionality: " functionality
            "App-state/User" @app-state/user)
  component)
