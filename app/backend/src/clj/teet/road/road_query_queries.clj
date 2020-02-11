(ns teet.road.road-query-queries
  "Road WFS queries for frontend"
  (:require [teet.db-api.core :as db-api :refer [defquery]]
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

(defquery :road/geometry
  {:doc "Fetch road geometry for road, carriageway and meter range."
   :context _
   :args {:keys [road carriageway start-m end-m]}
   :pre [(every? number? [road carriageway start-m end-m])]
   :project-id nil
   :authorization {}}
  (road-query/fetch-road-geometry (wfs-config)
                                  road carriageway start-m end-m))

(defquery :road/road-parts-for-coordinate
  {:doc "Fetch road parts for a clicked coordinate"
   :context _
   :args {:keys [coordinate distance]}
   :pre [(number? distance)
         (geo/point? coordinate)]
   :project-id nil
   :authorization {}}
  (road-query/fetch-road-parts-by-coordinate (wfs-config) coordinate distance))

(defquery :road/closest-road-part-for-coordinate
  {:doc "Fetch the closest road part for clicked coordinate"
   :context _
   :args {:keys [coordinate]}
   :pre [(geo/point? coordinate)]
   :project-id nil
   :authorization {}}
  (let [parts (road-query/fetch-road-parts-by-coordinate (wfs-config) coordinate 200)]
    (->> parts
         (map (fn [{g :geometry :as part}]
                (assoc part :distance (apply min (map #(geo/distance coordinate %) g)))))
         (sort-by :distance)
         first)))
