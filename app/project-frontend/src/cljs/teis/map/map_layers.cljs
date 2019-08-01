(ns teis.map.map-layers
  (:require [teis.ui.openlayers.mvt :as mvt]))

(defn mvt-layer [endpoint rpc-name parameters style-fn]
  (mvt/->MVT (str endpoint "/rpc/" rpc-name) ; base url
             rpc-name ; source-name
             "EPSG:3857" ; projection
             [2149379.365139117 8432408.084854249 3476299.593004654 7747259.817069946] ;nil ; extent
             99 ; z-index
             [] ; legend
             1 ; opacity
             nil nil ;; 1 20 ;; min and max resolution
             parameters
             style-fn))
