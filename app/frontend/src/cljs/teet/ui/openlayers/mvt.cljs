(ns teet.ui.openlayers.mvt
  "Create an MVT layer from maprender service"
    (:require [kuvataso.Lahde]
            [ol.layer.VectorTile]
            [ol.source.VectorTile]
            [ol.format.MVT]
            [ol.extent :as ol-extent]
            [teet.ui.openlayers.edistymispalkki :as palkki]
            [teet.ui.openlayers.taso :refer [Taso]]
            [cljs.core.async :as async]
            [clojure.string :as str]
            [ol.style.Style]
            [ol.style.Text]
            [taoensso.timbre :as log]
            [goog.object :as gobj])
    (:require-macros [cljs.core.async.macros :refer [go]]))

(defn mvt-url [base-url & params]
  (str base-url "?" (str/join "&"
                              (map (fn [[name val]]
                                     (str name "=" val))
                                   params))))

(defn hae-url [url source parameters coord pixel-ratio projection]
  (let [tile-grid (.getTileGridForProjection source projection)
        extent (.getTileCoordExtent tile-grid coord
                                    (ol-extent/createEmpty))
        [x1 y1 x2 y2] extent]
    (apply mvt-url
           url
           (merge {"xmin" x1 "ymin" y1 "xmax" x2 "ymax" y2
                   ;;"r" (.getResolution tile-grid (aget coord 0))
                   ;;"pr" pixel-ratio
                   }
                  parameters))))

(defn load-tile [tile url]
  ;;(.log js/console "Loading: " url)
  (let [format (.getFormat tile)]
    (.setLoader
     tile
     (fn []
       (-> (js/fetch url #js {:headers (doto (js/Headers.)
                                         (.append "Accept" "application/octet-stream"))})
           (.then #(.arrayBuffer %))
           (.then (fn [buf]
                    (let [features (.readFeatures format buf #js {:featureProjection "EPSG:3857"})
                          projection (.readProjection format buf)]
                      ;;(.log js/console "FEATURES: " features)
                      ;;(.log js/console "PROJECTION: " projection)
                      (.onLoad tile features
                               projection
                               (.getLastExtent format))))))))))

(defn post! [& args]
  ;; FIXME
  {})

(defrecord MVT [url source-name projection extent z-index selitteet opacity_ min-resolution max-resolution parametrit style-fn]
  Taso
  (aseta-z-index [this z-index]
    (assoc this :z-index z-index))
  (extent [this]
    nil)
  (aktiivinen? [this] true)
  (opacity [this] 1)
  (selitteet [this]
    selitteet)
  (paivita [this ol3 ol-layer aiempi-paivitystieto]
    (let [sama? (= parametrit aiempi-paivitystieto)
          luo? (nil? ol-layer)
          source (if (and sama? (not luo?))
                   (.getSource ol-layer)
                   (ol.source.VectorTile. #js {:projection projection
                                               :format (ol.format.MVT.)}))

          ol-layer (or ol-layer
                       (ol.layer.VectorTile.
                        #js {:source source
                             :wrapX true
                             :style style-fn
                             ;; PENDING 2019-07-02 image rendermode causes all images to rotate
                             ;; with the map when it is rotated.
                             ;; See: #166669426
                             ;;:renderMode "image"
                             }))]

      (.set ol-layer "teet-layer-name" (name source-name))
      (.setStyle ol-layer style-fn)

      (when (number? min-resolution)
        (.setMinResolution ol-layer min-resolution))

      (when (number? max-resolution)
        (.setMaxResolution ol-layer max-resolution))

      (.setOpacity ol-layer (or opacity_ 1))
      (.setTileLoadFunction source load-tile)
      (.setTileUrlFunction source (partial hae-url url source parametrit))
      (when luo?
        (.set ol-layer "teet-source" source-name)
        (.addLayer ol3 ol-layer))

      (when z-index
        (.setZIndex ol-layer z-index))

      (when (and (not luo?) (not sama?))
        ;; Jos ei luoda ja parametrit eivät ole samat
        ;; asetetaan uusi source ol layeriiin
        (.setSource ol-layer source))
      [ol-layer parametrit]))

  (hae-asiat-pisteessa [this koordinaatti extent]
    (let [ch (async/chan)]
      (go
        (let [asiat (<! (post! :karttakuva-klikkaus
                               {:parametrit (into {}
                                                  (map vec)
                                                  (partition 2 parametrit))
                                :koordinaatti koordinaatti
                                :extent extent}))]
          (doseq [asia asiat]
            (async/>! ch asia))
          (async/close! ch)))
      ch)))

(defn luo-mvt-taso [source-name projection extent selitteet opacity min-resolution max-resolution parametrit style-fn]
  (->MVT "maprender/mvt" source-name projection extent 99 selitteet opacity min-resolution max-resolution parametrit style-fn))
