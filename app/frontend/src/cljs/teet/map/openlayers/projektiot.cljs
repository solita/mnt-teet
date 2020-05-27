(ns teet.map.openlayers.projektiot
  "Karttaa varten tarvittavat extent alueet ja projektiot"
  (:require [ol.proj :as ol-proj]
            ["proj4" :as proj4-lib]))

(def proj4 (aget proj4-lib "default"))

(defonce define-epsg3067
  (let [defs (aget proj4 "defs")]
    (defs "EPSG:3301"
      "+proj=lcc +lat_1=59.33333333333334 +lat_2=58 +lat_0=57.51755393055556 +lon_0=24 +x_0=500000 +y_0=6375000 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs")
    (defs "urn:x-ogc:def:crs:EPSG:3067" "+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs")
    (defs "EPSG:3067" (defs "urn:x-ogc:def:crs:EPSG:3067"))
    (ol-proj/setProj4 proj4)))

(def estonian-extent
  "Estonian Coordinate System 1997 projected bounds"
  [282560.67 6381157.44
   734255.01 6658861.37])

#_(def teet-max-extent
  "Teet map max extent to allow user to navigate to."
  [61774.0 6541215.0 735067.0 7778528.0])

(def projektio (ol-proj/Projection. #js {:code   "EPSG:3301"
                                         :extent (clj->js estonian-extent)}))
