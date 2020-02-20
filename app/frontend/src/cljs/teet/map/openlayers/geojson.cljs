(ns teet.map.openlayers.geojson
  "GeoJSON layer from URL"
  (:require [ol.source.Vector]
            [ol.format.GeoJSON]
            [ol.layer.Vector]
            [teet.map.openlayers.layer :refer [Layer]]
            [clojure.string :as str]
            [teet.log :as log]
            postgrest-ui.impl.fetch))

(defn geojson-url [base-url & params]
  (str base-url "?" (str/join "&"
                              (map (fn [[name val]]
                                     (str name "=" val))
                                   params))))

(defn load-features [^ol.source.Vector source url-or-data content-type _extent _resolution _projection]
  ;;(log/info "LOAD " url-or-data ", extent:" extent ", resolution: " resolution ", projection: " projection)
  (let [add-features! (fn [json]
                        ;;(js/console.log "loaded geojson: " json)
                        (let [features (-> source
                                           .getFormat
                                           (.readFeatures json #js {"dataProjection" "EPSG:3301"}))]
                          (doto source
                            (.addFeatures features)
                            .refresh)))]
    (-> (cond
          (object? url-or-data)
          (add-features! url-or-data)

          (fn? url-or-data)
          (add-features! (url-or-data))

          :else
          (-> (@postgrest-ui.impl.fetch/fetch-impl
               url-or-data #js {:headers #js {"Accept" (or content-type
                                                           "application/octet-stream")}})
              (.then #(.json %))
              (.then add-features!))))))

(defrecord GeoJSON [source-name projection extent z-index opacity_ min-resolution max-resolution
                    url-or-data content-type style-fn on-change on-select]
  Layer
  (set-z-index [this z-index]
    (assoc this :z-index z-index))
  (extent [this]
    nil)
  (aktiivinen? [this] true)
  (opacity [this] 1)
  (selitteet [this]
    [])
  (paivita [this ol3 ol-layer aiempi-paivitystieto]
    (log/info "paivita! aiempi: " aiempi-paivitystieto)
    (let [sama? (= url-or-data aiempi-paivitystieto)
          luo? (nil? ol-layer)
          source (if (and sama? (not luo?))
                   (.getSource ol-layer)
                   (ol.source.Vector. #js {:projection projection
                                           :url url-or-data
                                           :format (ol.format.GeoJSON.
                                                    #js {:defaultDataProjection projection})}))

          ol-layer (or ol-layer
                       (ol.layer.Vector.
                        #js {:source source
                             :wrapX true}))]


      (.setStyle ol-layer style-fn)

      (when on-change
        (.on source "change" #(on-change {:extent (.getExtent source)
                                          :source source})))

      (when on-select
        (.set ol-layer "teet-on-select" on-select))

      (.setLoader source (partial load-features source url-or-data content-type))

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
      [ol-layer url-or-data]))

  (hae-asiat-pisteessa [this koordinaatti extent]
    nil))
