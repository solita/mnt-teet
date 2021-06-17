(ns teet.asset.asset-road-registry-import
  "Import assets to TEET from road registry WFS.
  Requires a mapping for an asset type that describes
  what components are created and what fields go to
  which attributes."
  (:require [teet.map.map-services :as map-services]
            [teet.road.road-query :as road-query]
            [hiccup.core :as h]
            [teet.util.coerce :refer [->long ->double ->bigdec]]
            [teet.util.geo :as geo]
            [teet.road.road-model :as road-model]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.log :as log]
            [cheshire.core :as cheshire]))


(defn- road-registry-objects [config type gml-area]
  (map-services/fetch-intersecting-objects-of-type
   config type gml-area))

(defn- gml-area [[x1 y1] [x2 y2]]
  (h/html
   [:gml:Polygon {:srsName "EPSG:3301"}
    [:gml:outerBoundaryIs
     [:gml:LinearRing
      [:gml:coordinates
       (str y1 "," x1 " "
            y2 "," x1 " "
            y2 "," x2 " "
            y1 "," x2 " "
            y1 "," x1)]]]]))

(defn line-segment-for-point-on-road [client point road-nr carriageway meters]
  (when-let [part (first
                   (filter
                    #(<= (:start-m %) meters (:end-m %))
                    (road-query/fetch-road-parts client road-nr carriageway)))]
    ;; Find the line segment that is closest (either start/end point)
    (first (sort-by (fn [[start end]]
                      (min (geo/distance start point)
                           (geo/distance end point)))
                    (partition 2 1 (:geometry part))))))

(defn point-to-perpendicular-line
  "Turn a point on the road centerline to a line start/end pair perpendicular to the road.

  client      the context for WFS queries
  point       the [x y] point on the road
  width       width (m) of the resulting start/end geometry
  road-nr     the road number
  carriageway the carriageway on the road
  meters      the meters on the road from whole road start"
  [client point width road-nr carriageway meters]
  (when-let [ls (line-segment-for-point-on-road client point road-nr carriageway meters)]
    (let [ang (geo/angle ls)]
      {:location/start-point (mapv bigdec (geo/offset-point point ang (/ width 2)))
       :location/end-point (mapv bigdec (geo/offset-point point ang (/ width -2)))})))

(declare recursive-convert)

(defprotocol Convert
  (convert [this ctx wfs-feature here key]
    "Run this conversion and return updated asset.
`ctx` has context information like WFS client
`wfs-feature` is the WFS feature being converted into asset
`here` is the current map of the asset being built
`key` is the key in the map this conversion was in"))

(defn from-wfs [wfs-field convert-fn]
  (reify Convert
    (convert [_ _ctx wfs-feature here key]
      (if-let [val (some-> wfs-feature (get wfs-field) convert-fn)]
        (assoc here key val)
        here))))

(defn from-point-to-perpendicular-line [width-field]
  (reify Convert
    (convert [_ ctx wfs-feature here _key]
      (let [road-nr (some-> wfs-feature :ms:tee_number ->long)
            carriageway (some-> wfs-feature :ms:soidutee_nr ->long)
            meters (some-> wfs-feature :ms:km ->bigdec road-model/km->m)
            point (some-> wfs-feature :geometry :coordinates)
            width (some-> wfs-feature (get width-field) ->double)]
        (if (and road-nr carriageway meters point width)
          (merge here
                 (point-to-perpendicular-line ctx point width road-nr carriageway meters))
          here)))))

(defn from-conditional
  "Conditional item, returns nil if condition isn't truthy.
  Do not use in map values as the whole map is removed."
  [condition value]
  (reify Convert
    (convert [_ ctx wfs-feature here _]
      (when (condition wfs-feature)
        (recursive-convert ctx wfs-feature value)))))

(def ^:private culvert-mapping
  {:asset/fclass :fclass/culvert
   :culvert/culvertpipenumber (from-wfs :ms:truup ->long)
   ::location (from-point-to-perpendicular-line :ms:trpik)
   :location/road-nr (from-wfs :ms:tee_number ->long)
   :location/carriageway (from-wfs :ms:soidutee_nr ->long)
   :location/start-km (from-wfs :ms:km ->bigdec)

   :common/status (from-wfs :ms:hinne_trhinne_xv
                            #(if (= % "0")
                               :item/abandoned
                               :item/inuse))

   :asset/components
   [{:component/ctype :ctype/culvertpipe
     ;; RR units in meters
     :culvertpipe/culvertpipediameter (from-wfs :ms:trava #(some-> % ->bigdec (* 1000M)))
     :culvertpipe/culvertpipelenght (from-wfs :ms:trpik ->bigdec)}

    ;; If otsad_trotsad_xv = 1, create 2 culverthead components
    (from-conditional #(= (:ms:otsad_trotsad_xv %) "1")
                      {:component/ctype :ctype/culverthead})
    (from-conditional #(= (:ms:otsad_trotsad_xv %) "1")
                      {:component/ctype :ctype/culverthead})

    ;; If otsad_trotsad_xv > 1, create 2 culvertprotection components
    (from-conditional #(some-> % :ms:otsad_trotsad_xv ->long (> 1))
                      {:component/ctype :ctype/culvertprotection})
    (from-conditional #(some-> % :ms:otsad_trotsad_xv ->long (> 1))
                      {:component/ctype :ctype/culvertprotection})]})

(defn- recursive-convert [ctx wfs-feature here]
  (cond
    (satisfies? Convert here)
    (convert here ctx wfs-feature here nil)

    (map? here)
    (reduce-kv (fn [m k v]
                 (if (satisfies? Convert v)
                   (convert v ctx wfs-feature m k)
                   (assoc m k (recursive-convert ctx wfs-feature v))))
               {} here)

    (vector? here)
    (into []
          (keep (partial recursive-convert ctx wfs-feature))
          here)

    :else here))

(defn import-road-registry-features! [conn ctx fclass wfs-type type-mapping [upper-left lower-right]]
  (let [objects (road-registry-objects ctx wfs-type (gml-area upper-left lower-right))]
    (log/debug "Found" (count objects) "WFS objects to import.")
    (when (seq objects)
      (d/transact
       conn
       {:tx-data
        [(list 'teet.asset.asset-tx/import-assets
               (environment/config-value :asset :default-owner-code)
               fclass
               (mapv #(merge
                       {:asset/road-registry-oid (->long (:ms:oid %))}
                       (recursive-convert ctx % type-mapping))
                     objects))]}))))

(def ^:private epsg-3301-bounds
  {:minx 282560.67
   :miny 6381157.44
   :maxx 734255.01
   :maxy 6658861.37})

(defn- estonia-slices
  "Return n slices for estonia from south to north"
  ([n] (estonia-slices n (:miny epsg-3301-bounds)))
  ([n y]
   (let [{:keys [minx miny maxx maxy]} epsg-3301-bounds
         ystep (/ (- maxy miny) n)]
     (when (< y maxy)
       (lazy-seq
        (cons
         [[minx y] [maxx (+ y ystep)]]
         (estonia-slices n (+ y ystep))))))))

(def ^:private import-config
  {:fclass/culvert {:wfs-type "ms:n_truup"
                    :mapping culvert-mapping}})

(defn import-ion [event]
  (let [fclass-name (-> event :input (cheshire/decode keyword) :fclass)
        fclass (keyword "fclass" fclass-name)
        {:keys [wfs-type mapping] :as c} (import-config fclass)
        client (environment/config-value :road-registry)]
    (if-not c
      (log/error "No import config found, specify proper :fclass name part"
                 {:fclass-name fclass-name})
      (do
        (future
          (doseq [slice (reverse (estonia-slices 100))]
            (log/info "Importing area slice: " slice)
            (try
              (import-road-registry-features!
               (environment/asset-connection)
               (assoc client :cache-atom (atom {})) ; cache per slice
               fclass wfs-type mapping
               slice)
              (catch Exception e
                (log/error e "Error while import slice")))))
        "{\"success\": true}"))))

(comment
  (import-ion {:input "{\"fclass\": \"culvert\"}"}))
