(ns teet.map.map-layers
  (:require [teet.map.openlayers.mvt :as mvt]
            [teet.map.openlayers.geojson :as geojson]
            [teet.map.openlayers :as openlayers]
            [clojure.string :as str]
            [teet.map.map-features :as map-features]
            [teet.log :as log]))

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
  [ctx _]
  (let [api-url (get-in ctx [:config :api-url])]
    {:projects
     (mvt-layer api-url
                "mvt_entities"
                {"type" "project"}
                map-features/project-line-style
                {:max-resolution project-pin-resolution-threshold})
     :project-pins
     (geojson-layer api-url
                    "geojson_entity_pins"
                    {"type" "project"}
                    map-features/project-pin-style
                    {:min-resolution project-pin-resolution-threshold
                     :fit-on-load? true})}))

(defmethod create-data-layer :default [_ {type :type}]
  (log/warn "Unsupported data layer type: " type)
  {})
