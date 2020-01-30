(ns teet.road.road-query-queries
  "Road WFS queries for frontend"
  (:require [teet.db-api.core :as db-api]
            [teet.road.road-query :as road-query]
            [teet.environment :as environment]))

(defmethod db-api/query :road/geometry [_ {:keys [road carriageway start-m end-m]}]
  (let [wfs-url  (environment/config-value :road-registry :wfs-url)]
    (when (nil? wfs-url)
      (throw (ex-info "No Teeregister WFS URL configured"
                      {:status 500
                       :error :teeregister-wfs-configuration-missing})))
    (road-query/fetch-road-geometry {:wfs-url wfs-url}
                                    road carriageway start-m end-m)))
