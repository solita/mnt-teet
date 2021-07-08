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
            [cheshire.core :as cheshire]
            [teet.integration.postgresql :as postgresql]
            [teet.asset.asset-geometry :as asset-geometry]
            [teet.util.collection :as cu]))


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

(defn from-wfs [wfs-feature wfs-field convert-fn]
  (some-> wfs-feature (get wfs-field) convert-fn))

(defn from-point-to-perpendicular-line [ctx wfs-feature width-field]
  (let [road-nr (some-> wfs-feature :ms:tee_number ->long)
        carriageway (some-> wfs-feature :ms:soidutee_nr ->long)
        meters (some-> wfs-feature :ms:km ->bigdec road-model/km->m)
        point (some-> wfs-feature :geometry :coordinates)
        width (some-> wfs-feature (get width-field) ->double)]
    (when (and road-nr carriageway meters point width)
      (point-to-perpendicular-line ctx point width road-nr carriageway meters))))

(defn culvert-mapping [ctx wfs-feature]
  (let [status (from-wfs wfs-feature
                             :ms:hinne_trhinne_xv
                             #(if (= % "0")
                                :item/abandoned
                                :item/inuse))
        id (str (:ms:oid wfs-feature))
        culvertpipenumber (from-wfs wfs-feature :ms:truup ->long)
        len (from-wfs wfs-feature :ms:trpik ->bigdec)
        trotsad (from-wfs wfs-feature :ms:otsad_trotsad_xv ->long)
        material (case (from-wfs wfs-feature :ms:trmater_trmater_xv ->long)
                   1 :material/concrete
                   2 :material/plastic
                   3 :material/structuralsteel
                   4 :material/sheetmetal
                   5 :material/stone
                   ;; For unmapped values, don't create any material
                   nil)
        head-material (case trotsad
                        2 :material/concrete
                        3 :material/stone
                        ;;4 :material/geogrid
                        nil)
        with-material (fn [id material component]
                        (merge component
                               (when material
                                 {:component/materials [{:db/id (str id "-m0")
                                                         :road-registry/id (str id "-m0")
                                                         :material/type material}]})))]
    (when-not culvertpipenumber
      (log/debug "Road registry culvert (oid: " id ") has no pipe number, not valid: " (from-wfs wfs-feature :ms:truup str)))
    (cu/without-nils
     (merge
      (from-point-to-perpendicular-line ctx wfs-feature :ms:trpik)
      (if (some? culvertpipenumber)
        {:culvert/culvertpipenumber culvertpipenumber}
        {:common/remark "missing truup (culvert pipe number) in road registry"})
      {:road-registry/id id
       :db/id id
       :asset/fclass :fclass/culvert
       :location/road-nr (from-wfs wfs-feature :ms:tee_number ->long)
       :location/carriageway (from-wfs wfs-feature :ms:soidutee_nr ->long)
       :location/start-km (from-wfs wfs-feature :ms:km ->bigdec)
       :common/status status
       :asset/components
       (vec
        (concat
         (when culvertpipenumber
           (for [i (range culvertpipenumber)
                 :let [id (str id "-pipe" i)]]
             (cu/without-nils
              (with-material
                id material
                {:component/ctype :ctype/culvertpipe
                 :road-registry/id id
                 :db/id id
                 :culvertpipe/culvertpipediameter (from-wfs wfs-feature :ms:trava #(some-> % ->bigdec (* 1000M)))
                 :culvertpipe/culvertpipelenght len
                 :component/quantity len}))))

         (when (= 1 trotsad)
           (list
            ;; If otsad_trotsad_xv = 1, create 2 culverthead components
            (with-material (str id "-head1") head-material
              {:component/ctype :ctype/culverthead
               :road-registry/id (str id "-head" 1)})
            (with-material (str id "-head2") head-material
              {:component/ctype :ctype/culverthead
               :road-registry/id (str id "-head" 2)})))

         (when (and (some? trotsad) (> trotsad 1))
           (list
            ;; If otsad_trotsad_xv > 1, create 2 culvertprotection components
            (with-material
              (str id "-prot1") head-material
              {:component/ctype :ctype/culvertprotection
               :road-registry/id (str id "-prot1")})
            (with-material
              (str id "-prot2") head-material
              {:component/ctype :ctype/culvertprotection
               :road-registry/id (str id "-prot2")})))))}))))

(defn import-road-registry-features! [conn sql-conn ctx fclass wfs-type type-mapping [upper-left lower-right]]
  (let [objects (road-registry-objects ctx wfs-type (gml-area upper-left lower-right))]
    (log/debug "Found" (count objects) "WFS objects to import.")
    (when (seq objects)
      (let [assets (vec (keep (partial type-mapping ctx) objects))
            rr-ids (mapv :road-registry/id assets)
            {db :db-after}
            (d/transact
             conn
             {:tx-data
              [{:db/id "datomic.tx"
                :road-registry/import-url (:wfs-url ctx)}
               (list 'teet.asset.asset-tx/import-assets
                     (environment/config-value :asset :default-owner-code)
                     fclass assets)]})]
        (postgresql/with-transaction sql-conn
          (doseq [[oid] (d/q '[:find ?oid
                               :where
                               [?e :road-registry/id ?id]
                               [?e :asset/oid ?oid]
                               :in $ [?id ...]]
                             db rr-ids)]
            (asset-geometry/update-asset! db sql-conn oid)))))))

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
              (environment/call-with-pg-connection
               (fn import-rr-feature [sql-conn]
                 (import-road-registry-features!
                  (environment/asset-connection)
                  sql-conn
                  (assoc client :cache-atom (atom {})) ; cache per slice
                  fclass wfs-type mapping
                  slice)))
              (catch Exception e
                (log/error e "Error while import slice")))))
        "{\"success\": true}"))))

(comment
  (import-ion {:input "{\"fclass\": \"culvert\"}"}))
