(ns teet.road.road-query-queries
  "Road WFS queries for frontend"
  (:require [teet.db-api.core :as db-api]
            [teet.road.road-query :as road-query]
            [teet.environment :as environment]))

(defn wfs-config []
  (let [wfs-url  (environment/config-value :road-registry :wfs-url)]
    (when (nil? wfs-url)
      (throw (ex-info "No Teeregister WFS URL configured"
                      {:status 500
                       :error :teeregister-wfs-configuration-missing})))
    {:wfs-url wfs-url}))

(defmethod db-api/query :road/geometry [_ {:keys [road carriageway start-m end-m]}]
  (road-query/fetch-road-geometry (wfs-config)
                                  road carriageway start-m end-m))

(defmethod db-api/query :road/road-parts-for-coordinate [_ {:keys [coordinate distance]}]
  (road-query/fetch-road-parts-by-coordinate (wfs-config) coordinate distance))
