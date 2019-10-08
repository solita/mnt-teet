(ns teet.road-visualization.road-visualization-controller
  (:require [tuck.core :as t]
            teet.common.common-controller))

(defrecord FetchRoadGeometry [road carriageway start-m end-m])

(extend-protocol t/Event
  FetchRoadGeometry
  (process-event [{:keys [road carriageway start-m end-m]} app]
    (t/fx app
          {:tuck.effect/type :rpc
           :endpoint (get-in app [:config :api-url])
           :rpc "geojson_road_geometry"
           :json? true
           :args {:road road
                  :carriageway carriageway
                  :start_m start-m
                  :end_m end-m}
           :result-path [:road]})))
