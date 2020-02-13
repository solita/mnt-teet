(ns teet.map.map-utils
  (:require ol.format.WKT))

(defn feature->wkt
  "Encode OpenLayers feature to Well-Known Text (WKT)."
  [feature]
  (.writeFeature (ol.format.WKT.) feature))
