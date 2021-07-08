(ns teet.road.road-query-queries
  "Road WFS and Teeregister API queries for frontend"
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.road.road-query :as road-query]
            [teet.environment :as environment]
            [teet.util.geo :as geo]
            [teet.gis.entity-features :as entity-features]
            [teet.road.road-model :as road-model]
            [teet.util.datomic :as du]
            [teet.util.collection :as cu]
            [teet.map.map-services :as map-services]
            [teet.road.teeregister-api :as teeregister-api]
            [clojure.spec.alpha :as s]
            [cheshire.core :as cheshire]
            [teet.util.coerce :refer [->bigdec ->long]])
  (:import (java.text NumberFormat)))

(defonce cache-options
  ;; For local development use:
  ;; uncomment this to cache everything (for caching in poor connectivity)
  #_{:cache-atom (atom {})}
  {})

(defn- tr-config
  "Return Teeregister URL configuration"
  []
  (let [wfs-url (environment/config-value :road-registry :wfs-url)
        wms-url (environment/config-value :road-registry :wms-url)]
    (when (or (nil? wfs-url)
              (nil? wms-url))
      (throw (ex-info "Teeregister URL configuration missing"
                      {:status 500
                       :wfs-url wfs-url
                       :wms-url wms-url
                       :error :teeregister-configuration-missing})))
    (merge
     {:wfs-url wfs-url
      :wms-url wms-url}
     cache-options)))

(defquery :road/geometry
  {:doc "Fetch road geometry for road, carriageway and meter range."
   :context _
   :args {:keys [road carriageway start-m end-m]}
   :pre [(every? number? [road carriageway start-m end-m])]
   :allowed-for-all-users? true}
  (road-query/fetch-road-geometry (tr-config)
                                  road carriageway start-m end-m))

(defquery :road/address-geometry-geojson
  {:doc "Fetch road geometries for addresses as GeoJSON"
   :context _
   :args {addr :road-address}
   :allowed-for-all-users? true}
  (let [c (tr-config)]
    ^{:format :raw}
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (cheshire/encode
            {:type "FeatureCollection"
             :features
             (for [{:keys [road-nr carriageway start-m end-m]} addr
                   :let [g (road-query/fetch-road-geometry
                            c road-nr carriageway start-m end-m)]]
               {:type "Feature"
                :geometry {:type "LineString"
                           :coordinates g}})})}))

(defquery :road/geometry-with-road-info
  {:doc "Fetch road geometry for road address, along with information about the road."
   :context _
   :args {:keys [road carriageway start-m end-m]}
   :pre [(every? number? [road carriageway start-m end-m])]
   :allowed-for-all-users? true}
  (let [config (tr-config)]
    {:geometry (road-query/fetch-road-geometry config road carriageway start-m end-m)
     :road-info (road-query/fetch-road-info config road carriageway)}))

(defquery :road/road-parts-for-coordinate
  {:doc "Fetch road parts for a clicked coordinate"
   :context _
   :args {:keys [coordinate distance]}
   :pre [(number? distance)
         (geo/point? coordinate)]
   :allowed-for-all-users? true}
  (road-query/fetch-road-parts-by-coordinate (tr-config) coordinate distance))

(defn- closest-road-part-for-coordinate [config coordinate]
  (let [parts (road-query/fetch-road-parts-by-coordinate config coordinate 200)]
    (->> parts
         (map (fn [{g :geometry :as part}]
                (assoc part :distance (apply min (map #(geo/distance coordinate %) g)))))
         (sort-by :distance)
         first)))

(defquery :road/closest-road-part-for-coordinate
  {:doc "Fetch the closest road part for clicked coordinate"
   :context _
   :args {:keys [coordinate]}
   :pre [(geo/point? coordinate)]
   :allowed-for-all-users? true}
  (closest-road-part-for-coordinate (tr-config) coordinate))

(defn road-properties [coordinate]
  (let [config (tr-config)
        road-part (closest-road-part-for-coordinate config coordinate)]
    {:road-part road-part}))

(defquery :road/road-properties-for-coordinate
  {:doc "Fetch the closest road part and road objects for clicked coordinate"
   :context _
   :args {:keys [coordinate]}
   :pre [(geo/point? coordinate)]
   :allowed-for-all-users? true}
  (road-properties coordinate))

(def ^:private fetch-wms-layers*
  (comp (memoize map-services/fetch-wms-layers)
        #(select-keys % [:wms-url])))

(def ^:private fetch-wfs-feature-types*
  (comp (memoize map-services/fetch-wfs-feature-types)
        #(select-keys % [:wfs-url])))

(defquery :road/wms-layers
  {:doc "Fetch the Teeregister WMS layers that can be shown"
   :context _
   :args _
   :allowed-for-all-users? true}
  (let [config (tr-config)]
    {:wms-url (:wms-url config)
     :layers (fetch-wms-layers* config)}))

(defquery :road/wfs-feature-types
  {:doc "Fetch the Teeregister WFS feature types that can be queried"
   :context _
   :args _
   :allowed-for-all-users? true}
  (let [config (tr-config)]
    {:wfs-url (:wfs-url config)
     :feature-types (fetch-wfs-feature-types* config)}))

;; PENDING: reorganize map queries to other ns, this isn't about roads as such
(defquery :road/eelis-wms-layers
  {:doc "Fetch the EELIS WMS layers"
   :context _
   :args _
   :allowed-for-all-users? true
   :config {wms-url [:eelis :wms-url]}}
  {:wms-url wms-url
   :layers (fetch-wms-layers* {:wms-url wms-url})})

(defquery :road/project-intersecting-objects
  {:doc "Fetch all road objects intersecting with project search area"
   :context {db :db}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :authorization {:project/read-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}}
  (let [integration-id (:integration/id (du/entity db [:thk.project/id id]))
        ctx (environment/config-map {:api-url [:api-url]
                                     :api-secret [:auth :jwt-secret]
                                     :wfs-url [:road-registry :wfs-url]})
        search-area (entity-features/entity-search-area-gml
                     ctx integration-id road-model/default-road-buffer-meters)
        feature-types (fetch-wfs-feature-types* ctx)]
    (into {}
          (map (fn [[type objects]]
                 [type {:feature-type (cu/find-first #(= (:name %) type) feature-types)
                        :objects objects}]))
          (road-query/fetch-all-intersecting-objects ctx search-area))))

(s/def ::geopoint (s/cat :x number? :y number?))
(s/def ::start ::geopoint)
(s/def ::end ::geopoint)
(s/def ::distance number?)
(s/def ::point ::geopoint)

(def ^:private locale (java.util.Locale. "et" "EE"))

(defn- decimal [d n]
  (.format (doto (NumberFormat/getNumberInstance)
             (.setMinimumFractionDigits d)
             (.setMaximumFractionDigits d)) n))

(defquery :road/by-2-geopoints
  {:doc "Return road for 2 geopoints within distance"
   :spec (s/keys :req-un [::start ::end ::distance])
   :config {client [:road-registry :api]}
   :args {:keys [start end distance]}
   :allowed-for-all-users? true}
  (let [road (->> (teeregister-api/road-by-2-geopoints
                   (teeregister-api/create-client client)
                   distance start end)
                  (sort-by :distance)
                  first)
        ls (some-> road :geometry (cheshire/decode keyword) :coordinates)
        km-format (partial decimal 6)
        m-format (partial decimal 3)]
    (merge
     {:road (-> road
                (cu/update-in-if-exists [:start-km] km-format)
                (cu/update-in-if-exists [:end-km] km-format)
                (cu/update-in-if-exists [:start-offset-m] m-format)
                (cu/update-in-if-exists [:end-offset-m] m-format))
      :start-point start
      :end-point end}
     (when ls
       {:start-offset-m (geo/line-string-point-offset ls start :start)
        :end-offset-m (geo/line-string-point-offset ls end :end)}))))

(defquery :road/by-geopoint
  {:doc "Return road for a single geopoint"
   :spec (s/keys :req-un [::point ::distance])
   :config {client [:road-registry :api]}
   :args {:keys [point distance]}
   :allowed-for-all-users? true}
  (let [road (first
              (teeregister-api/road-by-geopoint (teeregister-api/create-client client)
                                                distance point))
        p (some-> road :geometry (cheshire/decode keyword) :coordinates)]
    {:road road
     :start-point point
     :start-offset-m (when p
                       (geo/distance point p))}))


(defquery :road/line-by-road
  {:doc "Fetch line geometry based on road address from Teeregister API."
   :config {client [:road-registry :api]}
   :args {:keys [road-nr carriageway start-km end-km start-offset-m end-offset-m]}
   :allowed-for-all-users? true}
  (let [line (teeregister-api/line-by-road
              (teeregister-api/create-client client)
              (->long road-nr) (->long carriageway)
              (-> start-km ->bigdec road-model/km->m)
              (-> end-km ->bigdec road-model/km->m))]
    {:road-line line
     :start-point (if-let [offset (some-> start-offset-m ->bigdec double)]
                    (geo/line-string-offset-point line offset :start)
                    (first line))
     :end-point (if-let [offset (some-> end-offset-m ->bigdec double)]
                  (geo/line-string-offset-point line offset :end)
                  (last line))}))

(defquery :road/point-by-road
  {:doc "Fetch point geometry baed on road address from Teeregister API."
   :config {client [:road-registry :api]}
   :args {:keys [road-nr carriageway start-km]}
   :allowed-for-all-users? true}
  (teeregister-api/point-by-road (teeregister-api/create-client client)
                                 road-nr carriageway
                                 (-> start-km ->bigdec road-model/km->m)))

(defquery :road/autocomplete
  {:doc "Autocomplete road input by name or number (from Teeregister API)"
   :config {client [:road-registry :api]}
   :args {:keys [text]}
   :allowed-for-all-users? true}
  (teeregister-api/road-search (teeregister-api/create-client client)
                               text))


(defquery :road/name
  {:doc "Get road name for road nr and carriageway"
   :config {client [:road-registry :api]}
   :project-id nil
   :authorization {}
   :args {:keys [road-nr carriageway]}}
  (teeregister-api/road-name (teeregister-api/create-client client)
                             (->long road-nr)
                             (->long carriageway)))
