(ns teet.map.openlayers.background
  "Background layers.
  Creates background map layer based on :type."
  (:require [ol.source.WMTS]
            [ol.source.TileArcGISRest]
            [ol.source.TileWMS]
            [ol.source.Image]
            [ol.tilegrid.WMTS]
            [ol.tilegrid.TileGrid]
            [ol.layer.Tile]
            [ol.source.ImageWMS]
            [ol.layer.Image]
            [ol.source.OSM]
            [teet.map.openlayers.projektiot :as p]
            [taoensso.timbre :as log]

            [ol.format.WMTSCapabilities]))


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

#_(defn tile-url-with-swapped-xy [url]
  (clojure.string/replace url #"\bBBOX=([0-9%C.]*)"
                          (fn [[a]]
                            (let [parts (clojure.string/split (second (clojure.string/split a #"=")) "%2C")
                                  shuffled [(get parts 1) (get parts 0) (get parts 3) (get parts 2)]]
                              (str "BBOX=" (clojure.string/join "%2C" shuffled))))))

(defmethod create-background-layer :wms [{:keys [url layer style default] :as params}]
  (log/info "Create WMS layer: " params)
  (let [my-source (ol.source.ImageWMS.
                       #js {:url url
                            :params #js {:VERSION "1.1.1" :LAYERS layer :STYLES style :FORMAT "image/png"
                                         :CRS "EPSG:3301"}
                            :serverType 'mapserver'})
        ]
    #_(.setImageLoadFunction my-source (fn [image src]
                                       (log/info "image load fn called" src)
                                       (ol.source.Image.defaultImageLoadFunction image (tile-url-with-swapped-xy src))))
    (doto (ol.layer.Image.
           #js {:source my-source
                :opacity 0.5})
      (.setVisible default))))


#_(defmethod create-background-layer :arcgis-tiled [{:keys [url layer style default] :as params}]
  (log/info "Create arcgis-tiled layer: "params)
  (doto (ol.layer.Tile.
         #js {
              :source (ol.source.TileArcGISRest.
                       #js {:url url
                            :params #js {:VERSION "1.3.0" :LAYERS layer :STYLES style
                                         :CRS "EPSG:3301"}
                            }
                       )})
    (.setVisible default)))

(defmethod create-background-layer :wms-tiled [{:keys [url layer style default] :as params}]
  (log/info "Create WMS tile layer: " params)
  (doto (ol.layer.Tile.
         #js {
              :source (ol.source.TileWMS.
                       #js {:url url
                            :params #js {:VERSION "1.3.0" :LAYERS layer :STYLES style
                                         :CRS "EPSG:3301"}
                            :serverType "mapserver"}
                       )})
    (.setVisible default)))

(defmethod create-background-layer :osm [_]
  (log/info "Create OpenStreetMap layer.")
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
