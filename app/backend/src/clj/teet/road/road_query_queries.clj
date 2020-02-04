(ns teet.road.road-query-queries
  "Road WFS queries for frontend"
  (:require [teet.db-api.core :as db-api]
            [teet.road.road-query :as road-query]
            [teet.environment :as environment]
            [teet.util.geo :as geo]))

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

(defmethod db-api/query :road/closest-road-part-for-coordinate [_ {:keys [coordinate]}]
  (let [parts (road-query/fetch-road-parts-by-coordinate (wfs-config) coordinate 200)]
    (->> parts
         (map (fn [{g :geometry :as part}]
                (assoc part :distance (apply min (map #(geo/distance coordinate %) g)))))
         (sort-by :distance)
         first)))
