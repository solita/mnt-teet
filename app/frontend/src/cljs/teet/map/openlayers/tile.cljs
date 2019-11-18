(ns teet.map.openlayers.tile
  "WMS or WMTS tile layer"
  (:require [ol.layer.Tile]
            [ol.source.TileWMS]
            [ol.source.WMTS :as wmts-source]
            [ol.tilegrid.WMTS]
            [ol.format.WMTSCapabilities]
            [teet.map.openlayers.layer :refer [Layer]]
            postgrest-ui.impl.fetch))

;;FIXME
(declare promise?)
(declare p->)

(defn load-wmts-capabilities
  "Load WMTS capabilities document from given URL. Returns a promise"
  [url]
  (p-> (postgrest-ui.impl.fetch/fetch-impl
        (str url "?request=getcapabilities"))
       #(.text %)
       (fn [text]
         (.read (ol.format.WMTSCapabilities.) text))))


(defn wmts-options-from-capabilities [layer-options wmts-capabilities]
  (wmts-source/optionsFromCapabilities wmts-capabilities layer-options))

(defn lazy-wmts-source [url layer style]
  (p-> (load-wmts-capabilities url)
       (partial wmts-options-from-capabilities #js {:layer layer
                                                    :style style
                                                    :format "image/png"
                                                    :matrixSet "ETRS89_TM35-FIN"})
       #(ol.source.WMTS. %)))

(defrecord Tile [source-name projection extent z-index opacity_ type url params]
  Layer
  (set-z-index [this z-index]
    (assoc this :z-index z-index))
  (extent [this]
    nil)
  (aktiivinen? [this] true)
  (opacity [this] opacity_)
  (selitteet [this] [])
  (paivita [this ol3 ol-layer aiempi-paivitystieto]
    (let [sama? (= params aiempi-paivitystieto)
          luo? (nil? ol-layer)
          source (if (and sama? (not luo?))
                   (.getSource ol-layer)
                   (case type
                     :wms
                     (ol.source.TileWMS. #js {:url url
                                              :params #js {:LAYERS (:layer params)
                                                           :STYLES (:style params)}})

                     :wmts
                     (lazy-wmts-source url (:layer params) (:style params))))

          lazy-source? (promise? source)
          ol-layer (or ol-layer
                       (ol.layer.Tile.
                        #js {:source (when-not lazy-source?
                                       source)}))]

      (when lazy-source?
        ;; A new source promise was created, set it once it resolves
        (.then source #(.setSource ol-layer %)))

      (.setOpacity ol-layer (or opacity_ 1))
      (when luo?
        (.set ol-layer "teet-source" source-name)
        (.addLayer ol3 ol-layer))

      (when z-index
        (.setZIndex ol-layer z-index))

      (when (and (not luo?) (not sama?) (not lazy-source?))
        ;; Jos ei luoda ja parametrit eivät ole samat
        ;; asetetaan uusi source ol layeriiin
        (.setSource ol-layer source))

      [ol-layer params]))

  (hae-asiat-pisteessa [this koordinaatti extent]
    nil))



(defn luo-tile-taso [source-name projection extent opacity type url params]
  (->Tile source-name projection extent 1 opacity type url params))
