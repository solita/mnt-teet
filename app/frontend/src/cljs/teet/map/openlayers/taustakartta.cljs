(ns teet.map.openlayers.taustakartta
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
            [taoensso.timbre :as log]))


(defn tilegrid []
  (ol.tilegrid.WMTS.
   (clj->js {:origin (ol-extent/getTopLeft (.getExtent p/projektio))
             :resolutions [8192 4096 2048 1024 512 256 128 64 32 16 8 4 2 1 0.5 0.25]
             :matrixIds (range 16)
             :tileSize 256})))

(defn- wmts-layer [matrixset attribuutio url layer visible?]
  (doto (ol.layer.Tile.
         #js {:source
              (ol.source.WMTS. #js {:attributions [(ol.Attribution.
                                                    #js {:html attribuutio})]
                                    :url          url
                                    :layer        layer
                                    :matrixSet    matrixset
                                    :format       "image/png"
                                    :projection   p/projektio
                                    :tileGrid     (tilegrid)
                                    :style        "default"
                                    :wrapX        true})})
    (.setVisible visible?)))

(defmulti luo-taustakartta :type)

(defmethod luo-taustakartta :mml [{:keys [url layer default]}]
  (log/info "Luodaan MML karttataso: " layer)
  (wmts-layer "ETRS-TM35FIN" "MML" url layer default))

(defmethod luo-taustakartta :livi [{:keys [url layer default]}]
  (log/info "Luodaan livi karttataso: layer")
  (wmts-layer "EPSG:3067_PTP_JHS180" "Liikennevirasto" url layer default))

(defmethod luo-taustakartta :wms [{:keys [url layer style default] :as params}]
  (log/info "Luodaan WMS karttataso: " params)
  (doto (ol.layer.Image.
         #js {:source (ol.source.ImageWMS.
                       #js {:url url
                            :params #js {:VERSION "1.1.1" :LAYERS layer :STYLES style :FORMAT "image/png"}})})
    (.setVisible default)))

(defmethod luo-taustakartta :osm [_]
  (log/info "Luodaan OpenStreetMap karttataso.")
  (ol.layer.Tile. #js {:source (ol.source.OSM. (clj->js (merge {}
                                                               (when (= "dev" (.getAttribute js/document.body "data-environment"))
                                                                 {:url "http://localhost:4000/{z}/{x}/{y}.png"}))))}))

(defmethod luo-taustakartta :tms [{:keys [projection url]}]
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
