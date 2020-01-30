(ns teet.road-visualization.road-visualization-controller
  (:require [tuck.core :as t]
            teet.common.common-controller
            [teet.log :as log]))

(defrecord FetchRoadGeometry [road carriageway start-m end-m])
(defrecord FetchRoadAddressForCoordinate [coordinate])
(defrecord QueryFieldChange [field e])

(extend-protocol t/Event
  FetchRoadGeometry
  (process-event [{:keys [road carriageway start-m end-m]} app]
    (t/fx app
          {:tuck.effect/type :query
           :query :road/geometry
           :args {:road (js/parseInt road)
                  :carriageway (js/parseInt carriageway)
                  :start-m (js/parseInt start-m)
                  :end-m (js/parseInt end-m)}
           :result-path [:road-data :road-line-string]})
    #_(t/fx app
          {:tuck.effect/type :rpc
           :endpoint (get-in app [:config :api-url])
           :rpc "geojson_road_geometry"
           :json? true
           :args {:road road
                  :carriageway carriageway
                  :start_m start-m
                  :end_m end-m}
           :result-path [:road-data :road]}))

  FetchRoadAddressForCoordinate
  (process-event [{:keys [coordinate]} app]
    (log/info "COORD" coordinate)
    (t/fx app
          {:tuck.effect/type :rpc
           :endpoint (get-in app [:config :api-url])
           :rpc "road_address_for_coordinate"
           :json? true
           :method :GET
           :args {:x (first coordinate)
                  :y (second coordinate)}
           :result-path [:road-data :road-address]}))

  QueryFieldChange
  (process-event [{:keys [field e]} app]
    (let [value (-> e
                    .-target
                    .-value)]
      (assoc-in app [:query field] value))))
