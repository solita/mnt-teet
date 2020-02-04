(ns teet.road-visualization.road-visualization-controller
  (:require [tuck.core :as t]
            teet.common.common-controller
            [teet.log :as log]))

(defrecord FetchRoadGeometry [])
(defrecord FetchRoadAddressForCoordinate [coordinate])
(defrecord QueryFieldChange [field e])

(extend-protocol t/Event
  FetchRoadGeometry
  (process-event [_ app]
    (let [{:keys [road carriageway start-m end-m]}
          (get-in app [:road-data :form])]
      (t/fx app
            {:tuck.effect/type :query
             :query :road/geometry
             :args {:road (js/parseInt road)
                    :carriageway (js/parseInt carriageway)
                    :start-m (js/parseInt start-m)
                    :end-m (js/parseInt end-m)}
             :result-path [:road-data :road-line-string]})))

  FetchRoadAddressForCoordinate
  (process-event [{:keys [coordinate]} app]
    (log/info "COORD" coordinate)
    (t/fx (assoc-in app [:road-data :clicked-coordinate] coordinate)
          {:tuck.effect/type :query
           :query :road/closest-road-part-for-coordinate
           :args {:coordinate (vec coordinate)
                  :distance 200}
           :result-path [:road-data :road-parts-for-coordinate]}))

  QueryFieldChange
  (process-event [{:keys [field e]} app]
    (let [value (-> e
                    .-target
                    .-value)]
      (assoc-in app [:road-data :form field] value))))
