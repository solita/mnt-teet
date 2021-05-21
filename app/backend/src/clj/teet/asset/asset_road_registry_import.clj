(ns teet.asset.asset-road-registry-import
  "Import assets to TEET from road registry WFS.
  Requires a mapping for an asset type that describes
  what components are created and what fields go to
  which attributes."
  (:require [teet.road.road-query :as road-query]
            [teet.map.map-services :as map-services]
            [hiccup.core :as h]))


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

(comment
  (def c (teet.environment/config-value :road-registry))
  (def t "ms:n_truup")
  (def a (gml-area [680129.826,6497718.11221264] [684216.75673899,6501718.11221264]))

  (def culverts (road-registry-objects c t a)))
