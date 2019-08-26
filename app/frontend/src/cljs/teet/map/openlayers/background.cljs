(ns teet.map.openlayers.background
  "Taustakarttatasojen muodostus. Luo karttakomponentille annetun määrittelyn
  perusteella sopivat OpenLayersin WMTS tasojen objektit."
  (:require [ol.source.WMTS]
            [ol.tilegrid.WMTS]
            [ol.tilegrid.TileGrid]
            [ol.layer.Tile]
            [ol.source.ImageWMS]
            [ol.layer.Image]
            [ol.source.OSM]
            [ol.extent :as ol-extent]
            [teet.map.openlayers.projektiot :as p]
            [taoensso.timbre :as log]

            [ol.format.WMTSCapabilities]
            [ol.source.WMTS :as wmts-source]))


(defn maa-amet-tilegrid
  "Pre-loaded from getcapabilities request"
  []
  (ol.tilegrid.WMTS.
   #js {:extent #js [40500.000000 5993000.000000
                     1064500.000000 7017000.000000]
        :resolutions #js [4000 2000 1000 500 250 125 62.5 31.25 15.625 7.8125 3.90625 1.953125
                          0.9765625 0.48828125]
        :matrixIds #js ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11" "12" "13"]
        :tileSize 256}))


(defmulti create-background-layer :type)


(defmethod create-background-layer :maa-amet [{:keys [url layer default matrix-set style]
                                        :or {url "https://tiles.maaamet.ee/tm/wmts"
                                             matrix-set "LEST"
                                             style "default"}}]
  (log/info "Creating Maa-amet background map: " layer)
  (doto (ol.layer.Tile.
         #js {:source
              (ol.source.WMTS. #js {:attributions #js [(ol.Attribution.
                                                        #js {:html "Maa-amet"})]
                                    :url          url
                                    :layer        layer
                                    :matrixSet    matrix-set
                                    :format       "image/png"
                                    :projection   "EPSG:3301"
                                    :tileGrid     (maa-amet-tilegrid)
                                    :style        style
                                    :wrapX        true})})
    (.setVisible default)))

(defmethod create-background-layer :wms [{:keys [url layer style default] :as params}]
  (log/info "Luodaan WMS karttataso: " params)
  (doto (ol.layer.Image.
         #js {:source (ol.source.ImageWMS.
                       #js {:url url
                            :params #js {:VERSION "1.1.1" :LAYERS layer :STYLES style :FORMAT "image/png"}})})
    (.setVisible default)))

(defmethod create-background-layer :osm [_]
  (log/info "Luodaan OpenStreetMap karttataso.")
  (ol.layer.Tile. #js {:source (ol.source.OSM. (clj->js (merge {}
                                                               (when (= "dev" (.getAttribute js/document.body "data-environment"))
                                                                 {:url "http://localhost:4000/{z}/{x}/{y}.png"}))))}))

(defmethod create-background-layer :tms [{:keys [projection url]}]
  (log/info "Create TMS background map layer: " url)
  (ol.layer.Tile. #js {:source (ol.source.TileImage.
                                #js {:projection projection
                                     :tileGrid (ol.tilegrid.TileGrid.
                                                #js {:extent (clj->js p/estonian-extent)
                                                     :tileSize #js [256 256]})
                                     :url url})}))

;;   source: new ol.source.TileImage({
;;        projection: 'EPSG:3035',
;;        tileGrid: new ol.tilegrid.TileGrid({
;;          extent: [2409891.715, 1328424.080, 6143417.136, 5330401.505],
;;          tileSize: [200, 200],
;;          origin: [2409891.715, 1328424.080],
;;          resolutions: [4000, 3000, 2000, 1000]
;;        }),
;;        tileUrlFunction: function(coordinate) {
;;          return 'http://map-loader.appspot.com/srtm3035/'+coordinate[0]+
;;              '/'+ coordinate[1] +'/'+ coordinate[2] +'.png';
;;        }
;;      })
