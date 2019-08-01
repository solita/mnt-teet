(ns teis.ui.openlayers.featuret
  "OpenLayers featureiden luonti Clojure data kuvauksien perusteella"
  (:require [ol.Feature]

            [ol.geom.Polygon]
            [ol.geom.MultiPolygon]
            [ol.geom.Point]
            [ol.geom.Circle]
            [ol.geom.LineString]
            [ol.geom.MultiLineString]
            [ol.geom.GeometryCollection]

            [ol.style.Style]
            [ol.style.Fill]
            [ol.style.Stroke]
            [ol.style.Icon]
            [ol.style.Circle]
            [ol.style.Text]))


;; FIXME: remove this ns, we use MVT layers

(defmulti luo-feature :type)
(defmulti luo-geometria :type)
