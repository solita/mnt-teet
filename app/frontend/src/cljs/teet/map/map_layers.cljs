(ns teet.map.map-layers
  (:require [teet.map.openlayers.mvt :as mvt]
            [teet.map.openlayers.geojson :as geojson]
            [clojure.string :as str]))

(def ^:const default-projection "EPSG:3301")

(defn- url [base-url params]
  (str base-url "?"
       (str/join "&"
                 (map (fn [[name val]]
                        (str name "=" (when val
                                        (js/encodeURIComponent val))))
                      params))))

(defn mvt-layer [endpoint rpc-name parameters style-fn {:keys [min-resolution max-resolution z-index opacity]
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
             style-fn))

(defn geojson-layer [endpoint rpc-name parameters style-fn {:keys [min-resolution max-resolution
                                                                   z-index opacity]
                                                            :or {z-index 99
                                                                 opacity 1}}]
  (geojson/->GeoJSON rpc-name
                     default-projection
                     nil
                     z-index
                     opacity
                     min-resolution max-resolution
                     (url (str endpoint "/rpc/" rpc-name) parameters)
                     style-fn))
