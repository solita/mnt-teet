(ns teet.map.map-layers
  (:require [teet.map.openlayers.mvt :as mvt]
            [teet.map.openlayers.geojson :as geojson]
            [teet.map.openlayers :as openlayers]
            [teet.map.openlayers.layer :as layer]
            [teet.map.map-overlay :as map-overlay]
            [clojure.string :as str]
            [teet.map.map-features :as map-features]
            [teet.log :as log]
            [ol.layer.Tile]
            [ol.layer.Vector]
            [ol.source.TileWMS]
            [ol.source.Vector]
            [ol.format.WFS]
            [ol.format.GeoJSON]
            [ol.loadingstrategy :as ol-loadingstrategy]
            [teet.theme.theme-colors :as theme-colors]))


(def ^:const default-projection "EPSG:3301")

(defn- url [base-url params]
  (str base-url "?"
       (str/join "&"
                 (map (fn [[name val]]
                        (str name "=" (when val
                                        (js/encodeURIComponent val))))
                      params))))

(defn mvt-layer [endpoint rpc-name parameters style-fn
                 {:keys [min-resolution max-resolution z-index opacity on-select]
                  :or {z-index 99
                       opacity 1}}]
  (mvt/->MVT (str endpoint "/rpc/" rpc-name) ; base url
             rpc-name ; source-name
             default-projection
             nil ; extent
             z-index
             [] ; legend
             opacity
             min-resolution max-resolution
             parameters
             style-fn
             on-select))

(defn fit-extent
  [padding {extent :extent}]
  (openlayers/fit! extent {:padding padding}))

(defn geojson-layer [endpoint rpc-name parameters style-fn
                     {:keys [min-resolution max-resolution
                             z-index opacity
                             fit-on-load? fit-padding
                             on-load on-select
                             content-type
                             post?]
                      :or {z-index 99
                           opacity 1
                           fit-padding [0 0 0 0]
                           content-type "application/octet-stream"}}]
  (geojson/->GeoJSON rpc-name
                     default-projection
                     nil
                     z-index
                     opacity
                     min-resolution max-resolution
                     (if post?
                       {:url (str endpoint "/rpc/" rpc-name)
                        :payload parameters}
                       (url (str endpoint "/rpc/" rpc-name) parameters))
                     content-type
                     style-fn
                     (fn [layer]
                       (when on-load
                         (on-load layer))
                       (when fit-on-load?
                         (fit-extent fit-padding layer)))
                     on-select))

(defn geojson-data-layer [name geojson style-fn  {:keys [min-resolution max-resolution
                                                         z-index opacity fit-on-load?
                                                         fit-padding on-select on-load]
                                                  :or {z-index 99
                                                       opacity 1
                                                       fit-padding [0 0 0 0]}}]
  (geojson/->GeoJSON name
                     default-projection
                     nil
                     z-index
                     opacity
                     min-resolution max-resolution
                     geojson
                     :no-content-type
                     style-fn
                     (fn [layer]
                       (when on-load
                         (on-load layer))
                       (when fit-on-load?
                         (fit-extent fit-padding layer)))
                     on-select))

(defmulti create-data-layer
  "Create data layer from description. Dispatches on :type key.
  Must return a map of {layer-name-kw layer-record} and may return
  multiple layers.

  Context is a map containing relevant parts of the app state:
  :config "
  (fn [_ctx layer-description]
    (:type layer-description)))

(def ^:const project-pin-resolution-threshold 100)

(defmethod create-data-layer :projects
  [ctx {projects :projects}]
  (let [api-url (get-in ctx [:config :api-url])
        entity-query-options
        (if (vector? projects)
          ;; Have fetched projects, show only them
          {"ids" (str "{" (str/join "," (map :db/id projects)) "}")}
          ;; Show all projects
          {"type" "project"})]
    {:projects
     (mvt-layer api-url "mvt_entities" entity-query-options
                map-features/project-line-style
                {:max-resolution project-pin-resolution-threshold})
     :project-pins
     (geojson-layer api-url "geojson_entity_pins" entity-query-options
                    map-features/project-pin-style
                    {})}))

(defn- mvt-for-datasource-ids [api-url prefix datasource-ids style opts]
  (into {}
        (for [id datasource-ids]
          [(keyword (str prefix id))
           (mvt-layer api-url
                      "mvt_features"
                      {"datasource" id
                       "types" "{}"}
                      style opts)])))

(def ^:const project-restriction-resolution 20)

(defmethod create-data-layer :restrictions
  [ctx {:keys [datasource-ids]}]
  (let [api-url (get-in ctx [:config :api-url])]
    (mvt-for-datasource-ids api-url "restrictions-" datasource-ids
                            map-features/project-restriction-style
                            {:max-resolution project-restriction-resolution})))

(def ^:const cadastral-unit-resolution 5)

(defmethod create-data-layer :cadastral-units
  [ctx {:keys [datasource-ids]}]
  (let [api-url (get-in ctx [:config :api-url])]
    (mvt-for-datasource-ids api-url "cadastral-units-" datasource-ids
                            map-features/cadastral-unit-style
                            {:max-resolution cadastral-unit-resolution})))

(def create-wms-layer
  (memoize
   (fn [prefix wms-url layer]
     (let [name (str prefix layer)
           layer (ol.layer.Tile.
                  #js {:source
                       (ol.source.TileWMS.
                        #js {:url wms-url
                             :ratio 1
                             :hidpi false
                             ;; Teeregister doesn't return images for EPSG:3301
                             :projection "EPSG:4326"
                             :params #js {:LAYERS layer
                                          :FORMAT "image/png"}})})]
       (.set layer "teet-source" name)
       [name (layer/->OpenLayersTaso layer)]))))

(def create-wfs-layer
  (memoize
   (fn [prefix wfs-url feature-type style-fn]
     (let [name (str prefix feature-type)
           projection "EPSG:3301"
           source (ol.source.Vector.
                   #js {:projection projection
                        :strategy ol-loadingstrategy/bbox
                        :format (ol.format.GeoJSON.)})
           loader (fn [extent resolution _projection]
                    (let [url (str wfs-url
                                   "?service=WFS"
                                   "&version=1.1.0"
                                   "&request=GetFeature"
                                   "&typename=" feature-type
                                   "&outputFormat=application/json"
                                   "&srsname=" projection
                                   "&bbox=" (str/join "," extent) "," projection)]
                      (js/console.log "WFS URL:" url)
                      (-> (js/fetch url)
                          (.then #(.text %))
                          (.then
                           (fn [text]
                             (let [fmt (.getFormat source)]
                               (.addFeatures source
                                             (.readFeatures fmt text
                                                            #js {:dataProjection projection}))))))))
           layer (ol.layer.Vector. #js {:source source
                                        :style style-fn})]
       (.setLoader source loader)
       (.set layer "teet-source" name)
       (.set layer "teet-on-select"
             (partial map-overlay/feature-info-on-select
                      {:background-color theme-colors/gray-dark
                       :single-line? false
                       :height 300}))
       {name (layer/->OpenLayersTaso layer)}))))


(defmethod create-data-layer :teeregister
  [_ctx {:keys [wms-url selected]}]
  (into {}
        (map (partial create-wms-layer "teeregister-" wms-url))
        selected))

(defmethod create-data-layer :eelis
  [_ctx {:keys [wms-url selected]}]
  (into {}
        (map (partial create-wms-layer "eelis-" wms-url))
        selected))

(defmethod create-data-layer :heritage
  [_ctx _]
  (create-wfs-layer "heritage-"
                    "https://gsavalik.envir.ee/geoserver/keskkonnainfo/ows"
                    "keskkonnainfo:muinsusobjekt"
                    map-features/heritage-style))

(defmethod create-data-layer :heritage-protection-zones
  [_ctx _]
  (create-wfs-layer "heritae-protection-zones-"
                    "https://gsavalik.envir.ee/geoserver/keskkonnainfo/ows"
                    "keskkonnainfo:muinsusvoond"
                    map-features/heritage-protection-zone-style))

(defmethod create-data-layer :default [_ {type :type}]
  (log/warn "Unsupported data layer type: " type)
  {})
