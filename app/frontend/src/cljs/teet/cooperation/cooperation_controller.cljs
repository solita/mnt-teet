(ns teet.cooperation.cooperation-controller
  (:require [tuck.core :as t]
            [teet.log :as log]))

(defrecord SaveApplication [application])

(t/extend-protocol t/Event
  SaveApplication
  (process-event [{application :application} app]
    (log/info "Save application: " application)
    app))
