(ns teet.map.map-layers
  (:require [teet.map.openlayers.mvt :as mvt]
            [teet.map.openlayers.geojson :as geojson]
            [teet.map.openlayers :as openlayers]
            [clojure.string :as str]))

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
