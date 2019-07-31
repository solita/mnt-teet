(ns teis.projects.projects.projects-controller
  (:require [tuck.core :as t]))

(defrecord SetListingState [state])

(extend-protocol t/Event
  SetListingState
  (process-event [{state :state} app]
    (assoc-in app [:projects :listing] state)))
