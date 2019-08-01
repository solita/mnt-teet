(ns teis.ui.openlayers.geojson
  "GeoJSON layer from URL"
  (:require [ol.source.Vector]
            [ol.format.GeoJSON]
            [ol.layer.Vector]
            [teis.ui.openlayers.taso :refer [Taso]]))

(defrecord GeoJSON [source-name projection extent z-index opacity_ url style-fn]
  Taso
  (aseta-z-index [this z-index]
    (assoc this :z-index z-index))
  (extent [this]
    nil)
  (aktiivinen? [this] true)
  (opacity [this] 1)
  (selitteet [this]
    [])
  (paivita [this ol3 ol-layer aiempi-paivitystieto]
    (let [sama? (= 1 aiempi-paivitystieto)
          luo? (nil? ol-layer)
          source (if (and sama? (not luo?))
                   (.getSource ol-layer)
                   (ol.source.Vector. #js {:projection projection
                                           :url url
                                           :format (ol.format.GeoJSON.
                                                    #js {:defaultDataProjection projection})}))

          ol-layer (or ol-layer
                       (ol.layer.Vector.
                        #js {:source source
                             :wrapX true
                             :style style-fn}))]

      (.setOpacity ol-layer (or opacity_ 1))
      (when luo?
        (.set ol-layer "teis-source" source-name)
        (.addLayer ol3 ol-layer))

      (when z-index
        (.setZIndex ol-layer z-index))

      (when (and (not luo?) (not sama?))
        ;; Jos ei luoda ja parametrit eivät ole samat
        ;; asetetaan uusi source ol layeriiin
        (.setSource ol-layer source))
      [ol-layer 1]))

  (hae-asiat-pisteessa [this koordinaatti extent]
    nil))

(defn luo-geojson-taso [lahde projektio extent opacity url style-fn]
  (->GeoJSON lahde projektio extent 0 opacity url style-fn))
