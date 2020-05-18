(ns teet.map.map-layers
  (:require [teet.map.openlayers.mvt :as mvt]
            [teet.map.openlayers.geojson :as geojson]
            [teet.map.openlayers :as openlayers]
            [teet.map.openlayers.layer :as layer]
            [clojure.string :as str]
            [teet.map.map-features :as map-features]
            [teet.log :as log]
            [ol.source.ImageWMS]))

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
                             content-type]
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
                     (url (str endpoint "/rpc/" rpc-name) parameters)
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
                    {:min-resolution project-pin-resolution-threshold})}))

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

(defn create-wms-layer
  [prefix]
  (memoize
   (fn [wms-url layer]
     (let [name (str prefix layer)
           layer (ol.layer.Image.
                  #js {:source
                       (ol.source.ImageWMS.
                        #js {:url wms-url
                             :ratio 1
                             ;; Teeregister doesn't return images for EPSG:3301
                             :projection "EPSG:4326"
                             :params #js {:LAYERS layer
                                          :FORMAT "image/png"}})})]
       (.set layer "teet-source" name)
       [name (layer/->OpenLayersTaso layer)]))))

(defmethod create-data-layer :teeregister
  [_ctx {:keys [wms-url selected]}]
  (into {}
        (map (partial (create-wms-layer "teeregister-") wms-url))
        selected))

(defmethod create-data-layer :eelis
  [_ctx {:keys [wms-url selected]}]
  (into {}
        (map (partial (create-wms-layer "eelis-") wms-url))
        selected))

(defmethod create-data-layer :default [_ {type :type}]
  (log/warn "Unsupported data layer type: " type)
  {})
