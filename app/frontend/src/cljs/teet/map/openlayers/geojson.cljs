(ns teet.map.openlayers.geojson
  "GeoJSON layer from URL"
  (:require [ol.source.Vector]
            [ol.format.GeoJSON]
            [ol.layer.Vector]
            [teet.map.openlayers.taso :refer [Taso]]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn geojson-url [base-url & params]
  (str base-url "?" (str/join "&"
                              (map (fn [[name val]]
                                     (str name "=" val))
                                   params))))

(defn load-features [^ol.source.Vector source url extent resolution projection]
  (log/info "LOAD " url ", extent:" extent ", resolution: " resolution ", projection: " projection)
  (-> (js/fetch url #js {:headers #js {"Accept" "application/octet-stream"}})
      (.then #(.json %))
      (.then (fn [json]
               (log/info "loaded geojson: " json)
               (def got-js json)
               (let [features (-> source
                                  .getFormat
                                  (.readFeatures json #js {"dataProjection" "EPSG:3301"}))]
                 (doto source
                   (.addFeatures features)
                   .refresh))))))

(defrecord GeoJSON [source-name projection extent z-index opacity_ min-resolution max-resolution
                    url style-fn]
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
    (log/info "paivita! aiempi: " aiempi-paivitystieto)
    (let [sama? (= url aiempi-paivitystieto)
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

      (.setLoader source (partial load-features source url))

      (.setOpacity ol-layer (or opacity_ 1))

      (when (number? min-resolution)
        (.setMinResolution ol-layer min-resolution))
      (when (number? max-resolution)
        (.setMaxResolution ol-layer max-resolution))

      (when luo?
        (.set ol-layer "teet-source" source-name)
        (.addLayer ol3 ol-layer))

      (when z-index
        (.setZIndex ol-layer z-index))

      (when (and (not luo?) (not sama?))
        ;; Jos ei luoda ja parametrit eivät ole samat
        ;; asetetaan uusi source ol layeriiin
        (.setSource ol-layer source))
      [ol-layer url]))

  (hae-asiat-pisteessa [this koordinaatti extent]
    nil))
