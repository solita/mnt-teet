(ns teet.navigation.navigation-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [taoensso.timbre :as log]))

(defrecord ToggleDrawer [])

(extend-protocol t/Event
  ToggleDrawer
  (process-event [_ app]
    (update-in app [:navigation :open?] not)))
