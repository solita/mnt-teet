(ns teis.ui.openlayers.projektiot
  "Karttaa varten tarvittavat extent alueet ja projektiot"
  (:require [ol.proj :as ol-proj]
            ;[proj4 :as proj4]
            ))


#_(defonce define-epsg3067
         (do
           (proj4/defs "urn:x-ogc:def:crs:EPSG:3067" "+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs")
           (proj4/defs "EPSG:3067" (proj4/defs "urn:x-ogc:def:crs:EPSG:3067"))
           (ol-proj/setProj4 proj4)))

#_(def suomen-extent
  "Suomalaisissa kartoissa olevan projektion raja-arvot."
  [-548576.000000 6291456.000000 1548576.000000 8388608.000000])

#_(def teis-max-extent
  "Teis map max extent to allow user to navigate to."
  [61774.0 6541215.0 735067.0 7778528.0])

#_(def projektio (ol-proj/Projection. #js {:code   "EPSG:3067"
                                         :extent (clj->js suomen-extent)}))
