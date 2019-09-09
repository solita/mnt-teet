(ns teet.navigation.navigation-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]))

(defrecord ToggleDrawer [])
(defrecord GoToLogin [])

(extend-protocol t/Event
  ToggleDrawer
  (process-event [_ app]
    (update-in app [:navigation :open?] not))

  GoToLogin
  (process-event [_ app]
    (log/info "GoToLogin event")
    (t/fx app
          {::tuck-effect/type :navigate
           :page :login})))
