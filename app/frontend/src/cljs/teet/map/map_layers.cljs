(ns teet.map.map-layers
  (:require [teet.map.openlayers.mvt :as mvt]))

(defn mvt-layer [endpoint rpc-name parameters style-fn]
  (mvt/->MVT (str endpoint "/rpc/" rpc-name) ; base url
             rpc-name ; source-name
             "EPSG:3301" ; projection
             nil ; extent
             99 ; z-index
             [] ; legend
             1 ; opacity
             nil nil ;; 1 20 ;; min and max resolution
             parameters
             style-fn))
