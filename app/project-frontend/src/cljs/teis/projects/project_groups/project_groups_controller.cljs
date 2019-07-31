(ns teis.projects.project-groups.project-groups-controller
  "Tuck controller for project groups."
  (:require [tuck.core :as t]
            [taoensso.timbre :as log]))

(defrecord OpenProjectGroup [group])

(defrecord SetListingState [state])
(defrecord SetProjectGroupState [state])

(extend-protocol t/Event
  OpenProjectGroup
  (process-event [{group :group} app]
    (log/info "Open project group: " group)
    app)

  SetListingState
  (process-event [{state :state} app]
    (assoc-in app [:project-groups :listing] state))

  SetProjectGroupState
  (process-event [{state :state} app]
    (assoc app :project-group state)))
