(ns teet.project.road-controller
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [teet.util.collection :as cu]))

(defrecord HighlightRoadObject [geometry])

(extend-protocol t/Event
  HighlightRoadObject
  (process-event [{geometry :geometry} app]
    (common-controller/update-page-state
     app [:road/highlight-geometries]
     cu/toggle geometry)))
