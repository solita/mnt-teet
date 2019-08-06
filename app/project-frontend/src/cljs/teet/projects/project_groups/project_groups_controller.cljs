(ns teet.projects.project-groups.project-groups-controller
  "Tuck controller for project groups."
  (:require [tuck.core :as t]
            [taoensso.timbre :as log]
            [teet.routes :as routes]))

(defrecord ClearProjectGroupState [])

(defmethod routes/on-navigate-event :project-group [_]
  (->ClearProjectGroupState))

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
    (assoc app :project-group state))

  ClearProjectGroupState
  (process-event [_ app]
    (dissoc app :project-group)))
