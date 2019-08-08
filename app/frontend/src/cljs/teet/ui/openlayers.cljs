(ns teet.ui.openlayers
  "OpenLayers 3 kartta."
  (:require [cljs.core.async :refer [<! >! chan timeout alts!] :as async]
            [goog.style :as gstyle]

            [ol]
            [ol.Attribution]
            [ol.Map]
            [ol.MapEvent]
            [ol.Overlay] ;; popup
            [ol.View]
            [ol.events.condition :as condition]
            [ol.extent :as ol-extent]
            [ol.interaction :as ol-interaction]
            [ol.interaction.Select]
            [ol.layer.Layer]
            [ol.layer.Vector]
            [ol.source.Vector]

            [reagent.core :as reagent :refer [atom]]

            ;[teet.geo :as geo]
            ;[teet.style.base :as style-base]
            ;[teet.style.colors :as colors]
            ;[teet.ui.openlayers.featuret :refer [aseta-tyylit] :as featuret]
            [teet.ui.openlayers.geojson :as geojson]
            [teet.ui.openlayers.kuvataso :as kuvataso]
            [teet.ui.openlayers.mvt :as mvt]
            [teet.ui.openlayers.projektiot :refer [projektio estonian-extent]]
            [teet.ui.openlayers.taso :as taso]
            [teet.ui.openlayers.taustakartta :as taustakartta]
            [teet.ui.openlayers.tile :as tile]

            [taoensso.timbre :as log]

            [tuck.effect])

  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; EPSG:3857 = WGS 84 / Pseudo Mercator -- Spherical Mercator
;; Used by google maps, open street map
;;
;; EPSG:3301 = estonian coordinate system 1997
(def default-projection "EPSG:3301")



(def suomen-extent nil)

(def ^{:doc "Odotusaika millisekunteina, joka odotetaan että
 kartan animoinnit on valmistuneet." :const true}
  animaation-odotusaika 200)

(def ^{:doc "ol3 näkymän resoluutio alkutilanteessa" :const true}
  initial-resolution 1200)

(def ^{:doc "Pienin mahdollinen zoom-taso, johon käyttäjä voi zoomata ulos"
       :const true}
  min-zoom 0)
(def ^{:doc "Suurin mahdollinen zoom-taso, johon käyttäjä voi zoomata sisään"
       :const true}
  max-zoom 21)


(def tooltipin-aika 3000)

^{:doc "Set extent buffer to avoid zooming too close"}
(def extent-buffer-default 1000)

;; Atom for active map interactions, so removing them is easier
(defonce map-interactions (cljs.core/atom {}))


;; Näihin atomeihin voi asettaa oman käsittelijän kartan
;; klikkauksille ja hoveroinnille. Jos asetettu, korvautuu
;; kartan normaali toiminta.
;; Nämä ovat normaaleja cljs atomeja, eivätkä siten voi olla
;; reagent riippuvuuksia.
(defonce klik-kasittelija (cljs.core/atom []))
(defonce hover-kasittelija (cljs.core/atom []))

(defn aseta-klik-kasittelija!
  "Asettaa kartan click käsittelijän. Palauttaa funktion, jolla käsittelijä poistetaan.
  Käsittelijöitä voi olla useita samaan aikaan, jolloin vain viimeisenä lisättyä kutsutaan."
  [funktio]
  (swap! klik-kasittelija conj funktio)
  #(swap! klik-kasittelija (fn [kasittelijat]
                             (filterv (partial not= funktio) kasittelijat))))

(defn aseta-hover-kasittelija!
  "Asettaa kartan hover käsittelijän. Palauttaa funktion, jolla käsittelijä poistetaan.
  Käsittelijoitä voi olla useita samaan aikaan, jolloin vain viimeisenä lisättyä kutsutaan."
  [funktio]
  (swap! hover-kasittelija conj funktio)
  #(swap! hover-kasittelija (fn [kasittelijat]
                              (filterv (partial not= funktio) kasittelijat))))

;; Kanava, jolla voidaan komentaa karttaa
(def komento-ch (chan))


(defn show-popup! [lat-lng content]
  (go (>! komento-ch [::popup lat-lng content])))

(defn hide-popup! []
  (go (>! komento-ch [::hide-popup])))

(defn hide-popup-without-event! []
  (go (>! komento-ch [::hide-popup-without-event])))

(defn invalidate-size! []
  (go (>! komento-ch [::invalidate-size])))

(defn aseta-kursori! [kursori]
  (go (>! komento-ch [::cursor kursori])))

(defn aseta-tooltip! [x y teksti]
  (go (>! komento-ch [::tooltip x y teksti])))

;;;;;;;;;
;; Define the React lifecycle callbacks to manage the OpenLayers
;; Javascript objects.

(declare update-ol3-geometries)


(def ^:export the-kartta (atom nil))

(defmethod tuck.effect/process-effect :refresh-layer [_ {layer-name :layer}]
  (when-let [layer (some #(when (= (.get % "teet-layer-name") (name layer-name)) %)
                         (.getArray (.getLayers @the-kartta)))]
    (log/info "Refreshing layer " layer)
    (.clear (.getSource layer))
    (.refresh (.getSource layer))))

;; expose map to browser for development and debugging
(defn ^:export get-the-kartta [] @the-kartta)

(defn set-map-size! [w h]
  (.setSize @the-kartta (clj->js [w h])))

(defn keskita-kartta-pisteeseen! [keskipiste]
  (when-let [ol3 @the-kartta]
    (.setCenter (.getView ol3) (clj->js keskipiste))))

(defn keskita-kartta-alueeseen! [alue]
  (assert (vector? alue) "Alueen tulee vektori numeroita")
  (assert (= 4 (count alue)) "Alueen tulee olla vektori [minx miny maxx maxy]")
  (when-let [ol3 @the-kartta]
    (let [view (.getView ol3)]
      (.fit view (clj->js alue) (.getSize ol3)))))

(defn extent-sisaltaa-extent? [iso pieni]
  (assert (and (vector? iso) (vector? pieni)) "Alueen tulee vektori numeroita")
  (assert (and (= 4 (count iso)) (= 4 (count pieni)))
          "Alueen tulee olla vektori [minx miny maxx maxy]")

  (ol/extent.containsExtent (clj->js iso) (clj->js pieni)))

(defn ^:export debug-keskita [x y]
  (keskita-kartta-pisteeseen! [x y]))

(defn ^:export invalidate-size []
  (.invalidateSize @the-kartta))

(defn kartan-extent []
  (when-let [k @the-kartta]
    (.calculateExtent (.getView k) (.getSize k))))

(defonce openlayers-kartan-leveys (atom nil))
(defonce openlayers-kartan-korkeus (atom nil))

#_(defn luo-kuvataso
  "Luo uuden kuvatason joka hakee serverillä renderöidyn kuvan.
Ottaa sisään vaihtelevat parametri nimet (string) ja niiden arvot.
Näkyvän alueen ja resoluution parametrit lisätään kutsuihin automaattisesti."
  [lahde selitteet opacity min-resolution max-resolution & parametri-nimet-ja-arvot]
  (kuvataso/luo-kuvataso nil suomen-extent selitteet opacity min-resolution max-resolution
                         (concat ["_" (name lahde)]
                                 parametri-nimet-ja-arvot)))

#_(defn luo-mvt-taso
  "Luo uuden MVT (Mapbox Vector Tile) tason, joka hakee palvelimelta vektoridataa."
  [lahde selitteet opacity min-resolution max-resolution style-fn & parametri-nimet-ja-arvot]
  (mvt/luo-mvt-taso lahde projektio suomen-extent selitteet
                    opacity min-resolution max-resolution
                    (concat ["_" (name lahde)]
                            parametri-nimet-ja-arvot)
                    style-fn))

#_(defn luo-tile-taso
  "Luo WMS tai WMTS lähteestä tason"
  [lahde opacity type url layer style]
  (tile/luo-tile-taso lahde projektio suomen-extent opacity type url {:layer layer :style style}))

#_(defn luo-geojson-taso
  "Luo uuden GeoJSON tason, joka hakee tiedot annetusta URL-osoitteesta."
  [lahde opacity url style-fn]
  (geojson/luo-geojson-taso lahde projektio suomen-extent opacity url style-fn ))

(defn sama-kuvataso? [vanha uusi]
  (kuvataso/sama? vanha uusi))

(defn keskipiste
  "Laskee geometrian keskipisteen extent perusteella"
  [geometria]
  (let [[x1 y1 x2 y2] (.getExtent geometria)]
    [(+ x1 (/ (- x2 x1) 2))
     (+ y1 (/ (- y2 y1) 2))]))

(defn feature-geometria [feature layer]
  (or
   ;; Normal geometry layer item
   (.get feature "teet-geometria")

   ;; Background layer item (from vector tile)
   {:map/tooltip (.get feature "tooltip")
    :map/type (.get layer "teet-source")
    :map/id (.get feature "id")}))

(defn- tapahtuman-geometria
  "Hakee annetulle ol3 tapahtumalle geometrian. Palauttaa ensimmäisen löytyneen
  geometrian."
  ([this e] (tapahtuman-geometria this e true))
  ([this e lopeta-ensimmaiseen?]
   (let [geom (volatile! [])
         {:keys [ol3]} (reagent/state this)]
     (.forEachFeatureAtPixel ol3 (.-pixel e)
                             (fn [feature layer]
                               (vswap! geom conj (feature-geometria feature layer))
                               lopeta-ensimmaiseen?)
                             ;; Funktiolle voi antaa options, jossa hitTolerance. Eli radius, miltä featureita haetaan.
                             )

     (cond
       (empty? @geom)
       nil

       lopeta-ensimmaiseen?
       (first @geom)

       :else @geom))))

(defn- laske-kartan-alue [ol3]
  (.calculateExtent (.getView ol3) (.getSize ol3)))

(defn- tapahtuman-kuvaus
  "Tapahtuman kuvaus ulkoisille käsittelijöille"
  [this e]
  (let [c (.-coordinate e)
        tyyppi (.-type e)]
    {:tyyppi   (case tyyppi
                 "pointermove" :hover
                 "click" :click
                 "singleclick" :click
                 "dblclick" :dbl-click)
     :geometria (tapahtuman-geometria this e)
     :sijainti [(aget c 0) (aget c 1)]
     :x        (aget (.-pixel e) 0)
     :y        (aget (.-pixel e) 1)}))

(defn- aseta-postrender-kasittelija [_ ol3 on-postrender]
  (.on ol3 "postrender"
       (fn [e]
         (when on-postrender
           (on-postrender e)))))

(defn- aseta-zoom-kasittelija [_ ol3 on-zoom]
  (.on (.getView ol3) "change:resolution"
       (fn [e]
         (when on-zoom
           (on-zoom e (laske-kartan-alue ol3))))))

(defn- aseta-drag-kasittelija [_ ol3 on-move]
  (.on ol3 "pointerdrag" (fn [e]
                           (when on-move
                             (on-move e (laske-kartan-alue ol3))))))

(defn- aseta-klik-kasittelija [this ol3 on-click on-select]
  (.on ol3 "singleclick"
       (fn [e]
         (if-let [kasittelija (peek @klik-kasittelija)]
           (kasittelija (tapahtuman-kuvaus this e))

           (if-let [g (tapahtuman-geometria this e true)]
             (do
               (log/info "on-select" g)
               (when on-select (on-select [g] e)))
             (do
               (log/info "on-click" e)
               (aset js/window "EVT" e)
               (when on-click (on-click e))))))))

;; dblclick on-clickille ei vielä tarvetta - zoomaus tulee muualta.
(defn- aseta-dblclick-kasittelija [this ol3 on-click on-select]
  (.on ol3 "dblclick"
       (fn [e]
         (if-let [kasittelija (peek @klik-kasittelija)]
           (kasittelija (tapahtuman-kuvaus this e))
           (when on-select
             (when-let [g (tapahtuman-geometria this e false)]
               (on-select g e)))))))


(defn aseta-hover-kasittelija [this ol3]
  (.on ol3 "pointermove"
       (fn [e]
         (if-let [kasittelija (peek @hover-kasittelija)]
           (kasittelija (tapahtuman-kuvaus this e))

           (reagent/set-state this
                              (if-let [g (tapahtuman-geometria this e)]
                                {:hover (assoc g
                                               :x (aget (.-pixel e) 0)
                                               :y (aget (.-pixel e) 1))}
                                {:hover nil}))))))


(defn keskita!
  "Keskittää kartan näkymän annetun featureen sopivaksi."
  [ol3 feature]
  (let [view (.getView ol3)
        extent (.getExtent (.getGeometry feature))]
    (.fit view extent (.getSize ol3))))

(defn- poista-openlayers-popup!
  "Älä käytä tätä suoraan, vaan kutsu poista-popup! tai
  poista-popup-ilman-eventtia!"
  [this]
  (let [{:keys [ol3 popup]} (reagent/state this)]
    (when popup
      (.removeOverlay ol3 popup)
      (reagent/set-state this {:popup nil}))))

(defn- poista-popup!
  "Poistaa kartan popupin, jos sellainen on."
  [this]
  #_(t/julkaise! {:aihe :popup-suljettu})
  (poista-openlayers-popup! this))

(defn- poista-popup-ilman-eventtia!
  "Poistaa kartan popupin, jos sellainen on, eikä julkaise popup-suljettu
  eventtiä."
  [this]
  (poista-openlayers-popup! this))

(defn luo-overlay [koordinaatti sisalto]
  (let [elt (js/document.createElement "span")]
    (reagent/render sisalto elt)
    (ol.Overlay. (clj->js {:element   elt
                           :position  koordinaatti
                           :stopEvent false}))))


(defn- nayta-popup!
  "Näyttää annetun popup sisällön annetussa koordinaatissa.
  Mahdollinen edellinen popup poistetaan."
  [this koordinaatti sisalto]
  (let [{:keys [ol3 popup]} (reagent/state this)]
    (when popup
      (.removeOverlay ol3 popup))
    (let [popup (luo-overlay
                 koordinaatti
                 [:div.ol-popup
                  [:a.ol-popup-closer.klikattava
                   {:on-click #(do (.stopPropagation %)
                                   (.preventDefault %)
                                   (poista-popup! this))}]
                  sisalto])]
      (.addOverlay ol3 popup)
      (reagent/set-state this {:popup popup}))))

;; Käytetään the-karttaa joka oli aiemmin "puhtaasti REPL-tunkkausta varten"
(defn nykyinen-zoom-taso []
  (some-> @the-kartta (.getView) (.getZoom)))

(defn aseta-zoom [zoom & [duration]]
  (some-> @the-kartta (.getView) (.setZoom #js {:zoom zoom
                                                :duration (or duration 250)})))

(defn animate-zoom [zoom & [duration]]
  (some-> @the-kartta (.getView) (.animate #js {:zoom zoom
                                                :duration (or duration 250)})))

(defn extent-keskipiste [[minx miny maxx maxy]]
  (let [width (- maxx minx)
        height (- maxy miny)]
    [(+ minx (/ width 2))
     (+ miny (/ height 2))]))


(defn- create-dragbox-interaction [opts]
  (ol-interaction/DragBox. (clj->js opts)))

(defn- create-select-interaction [opts]
  (ol-interaction/Select. (clj->js opts)))


(defn add-interaction! [interaction key]
  (some-> @the-kartta (.addInteraction interaction))
  (swap! map-interactions assoc key interaction))

(defn remove-interaction! [key]
  (some-> @the-kartta (.removeInteraction (key @map-interactions)))
  (swap! map-interactions dissoc key))


(defn enable-bbox-select! [{:keys [box-style]}]
  (let [drag-box (create-dragbox-interaction {:condition condition/platformModifierKeyOnly})
        select (create-select-interaction {})]

    (add-interaction! drag-box :bbox-drag-box)
    (add-interaction! select :bbox-select)

    drag-box))

(defn disable-bbox-select! []
  (remove-interaction! :bbox-select)
  (remove-interaction! :bbox-drag-box))

(defn resize-map [m]
  (let [target-element (.getTargetElement m)
        size (gstyle/getContentBoxSize target-element)]
    (.setSize m (.-width size) (.-height size))))

(defn- orientate-map [this {:keys [geometries zoom-to-geometries extent-buffer
                                   center-on-geometry rotation]}]

  (let [{prev-zoom-to-geometries :zoom-to-geometries
         prev-zoom-target-geoms :zoom-target-geometries
         ol3 :ol3}
        (reagent/state this)
        zoom-target-geoms (when zoom-to-geometries (map
                                                     (fn [key]
                                                       (first (key geometries)))
                                                     (sort (keys zoom-to-geometries))))
        rotation (or rotation 0)
        view (.getView ol3)]
    (reagent/set-state this {:zoom-to-geometries zoom-to-geometries
                             :zoom-target-geometries zoom-target-geoms})

    (when (and zoom-target-geoms
               ;; Check if zoom-to target geometries have actually changed
               (or (not= zoom-target-geoms prev-zoom-target-geoms)
                   ;; Otherwise, check if zoom-to-geometries key-value pairs have been changed.
                   ;; This allows programmatically forcing re-zoom by changing zoom-to-geometries map values.
                   (not= zoom-to-geometries prev-zoom-to-geometries)))

      (let [geometry-layers (:geometry-layers (reagent/state this))
            zoom-to-extents (into []
                                  (comp
                                    (keep (fn [[key [layer geometries]]]
                                            (when (and (zoom-to-geometries key)
                                                       (seq geometries))
                                              layer)))
                                    (map #(-> % .getSource .getExtent)))
                                  geometry-layers)
            combined-extent (reduce (fn [e1 e2]
                                      (when (and e1 e2)
                                        (ol-extent/extend e1 e2)))
                                    zoom-to-extents)
            ;; Center-on-geometry can contain specific fitting options, so it is then a map.
            ;;   Otherwise, it is just a key to target geometry.
            center-on-geom-target (if (map? center-on-geometry)
                                 (:target-geometry center-on-geometry)
                                 center-on-geometry)
            center-geometry (some-> center-on-geom-target
                                    geometry-layers
                                    first)
            center-extent (some-> center-geometry
                                  .getSource
                                  .getExtent)]
        (when (seq zoom-to-extents)
          (if center-extent
            (let [[x1 y1 x2 y2] combined-extent
                  ;; Compute max distance to from center extent to combined extend corners
                  max-dist-to-corner (apply max
                                            [:FIXME]
                                            #_[(geo/distance (ol-extent/getCenter center-extent)
                                                           [x1 y1])
                                             (geo/distance (ol-extent/getCenter center-extent)
                                                           [x1 y2])
                                             (geo/distance (ol-extent/getCenter center-extent)
                                                           [x2 y1])
                                             (geo/distance (ol-extent/getCenter center-extent)
                                                           [x2 y2])])

                  ;; Add manually some extra buffer from map state if defined
                  ;; This allows manual fine tuning of center-on-geometry and
                  ;; zoom-to-extents fitting.
                  max-dist-to-corner (if (number? (:extent-buffer center-on-geometry))
                                       (+ max-dist-to-corner(:extent-buffer center-on-geometry))
                                       max-dist-to-corner)]

              ;; Center extent specified
              (if (:center-only? center-on-geometry)
                ;; Center - does not affect zoom level
                ;; https://openlayers.org/en/v4.6.5/apidoc/ol.View.html#setCenter
                (.setCenter view (ol-extent/getCenter center-extent))

                ;; Fit - affects zoom level
                ;; https://openlayers.org/en/v4.6.5/apidoc/ol.View.html#fit
                (.fit view
                      (ol-extent/buffer
                        (ol-extent/boundingExtent #js [(ol-extent/getCenter center-extent)])
                        ;; Buffer size
                        max-dist-to-corner)
                      (clj->js (merge {:size (.getSize ol3)
                                       ;; Padding top, right, bottom and left
                                       :padding #js [125 100 125 100] :constrainResolution true}
                                      ;; Use min-resolution to control min zoom level of center extent fitting.
                                      (when (number? (:min-resolution center-on-geometry))
                                        {:minResolution (:min-resolution center-on-geometry)})

                                      ;; Maximum zoom level that we zoom to. If minResolution is given, this property is ignored.
                                      (when (number? (:max-zoom center-on-geometry))
                                        {:maxZoom (:max-zoom center-on-geometry)}))))))
            ;; No center, just fit view to combined extent
            (.fit view
                  (ol-extent/buffer combined-extent (or extent-buffer extent-buffer-default))
                  #js {:size (.getSize ol3)
                       ;; Padding top, right, bottom and left
                       :padding #js [125 100 125 100] :constrainResolution true})))))

    ;; Set map rotation (defaults to 0, no rotation) if rotation has changed.
    (when (not= (.getRotation view) rotation)
      (.setRotation view rotation))))

(defonce window-resize-listener-atom (atom nil))

(defn- on-container-resize [this]
  (let [uusi-leveys (.-offsetWidth
                      (aget (.-childNodes (reagent/dom-node this)) 0))
        uusi-korkeus (.-offsetHeight
                       (aget (.-childNodes (reagent/dom-node this)) 0))]

    (when-not (and (= uusi-leveys
                      @openlayers-kartan-leveys)
                   (= uusi-korkeus
                      @openlayers-kartan-korkeus))
      (reset! openlayers-kartan-leveys uusi-leveys)
      (reset! openlayers-kartan-korkeus uusi-korkeus)

      (invalidate-size!))))



(defn- remove-window-resize-listener []
  (when @window-resize-listener-atom
    (.removeEventListener js/window "resize" @window-resize-listener-atom)))

(defn- set-window-resize-listener [this]
  (remove-window-resize-listener)
  (reset! window-resize-listener-atom (partial on-container-resize this))
  (.addEventListener js/window "resize" @window-resize-listener-atom))


(defn- ol3-did-mount [this current-zoom]
  "Initialize OpenLayers map for a newly mounted map component."
  (let [{layers :layers :as mapspec} (:mapspec (reagent/state this))
        interaktiot (let [oletukset (ol-interaction/defaults
                                     #js {:mouseWheelZoom true
                                          :dragPan        false})]
                      ;; ei kinetic-ominaisuutta!
                      (.push oletukset (ol-interaction/DragPan. #js {}))
                      oletukset)

        ;; Re-add later added interactions (if there are any) on re-mount
        _ (doseq [interaction (vals @map-interactions)]
            (.push interaktiot interaction))

        ;; NOTE: Currently disabled, because implement our own map control tools
        ;; kontrollit (ol-control/defaults #js {})

        map-optiot (clj->js {:layers       (mapv taustakartta/luo-taustakartta layers)
                             :target       (:id mapspec)
                             :controls [] ;; :controls     kontrollit
                             :interactions interaktiot})
        ol3 (ol/Map. map-optiot)

        ;; NOTE: Currently disabled, because implement our own map control tools
        ;; _ (.addControl ol3 (tasovalinta/tasovalinta ol3 layers))

        _ (reset!
            openlayers-kartan-leveys
            (.-offsetWidth (aget (.-childNodes (reagent/dom-node this)) 0)))

        _ (reset!
            openlayers-kartan-korkeus
            (.-offsetHeight (aget (.-childNodes (reagent/dom-node this)) 0)))

        _ (reset! the-kartta ol3)

        _ (set-window-resize-listener this)



        extent (:extent mapspec)
        center (:center mapspec)
        unmount-ch (chan)]


    ;; Aloitetaan komentokanavan kuuntelu
    (go-loop [[[komento & args] ch] (alts! [komento-ch unmount-ch])]
             (when-not (= ch unmount-ch)
               (case komento

                 ::popup
                 (let [[coordinate content] args]
                   (nayta-popup! this coordinate content))

                 ::invalidate-size
                 (do
                   (.updateSize ol3)
                   (.render ol3))

                 ::hide-popup
                 (poista-popup! this)

                 ::hide-popup-without-event
                 (poista-popup-ilman-eventtia! this)

                 ::cursor
                 (let [[cursor] args
                       vp (.-viewport_ ol3)
                       style (.-style vp)]
                   (set! (.-cursor style) (case cursor
                                            :crosshair "crosshair"
                                            :progress "progress"
                                            "")))
                 ::tooltip
                 (let [[x y teksti] args]
                   (reagent/set-state this
                                      {:hover {:x x :y y :tooltip teksti}})))
               (recur (alts! [komento-ch unmount-ch]))))

    (.setView
     ol3 (ol.View. (clj->js (merge {:center (clj->js (or center (extent-keskipiste extent)))
                                    :resolution initial-resolution
                                    :maxZoom max-zoom
                                    :minZoom min-zoom
                                    ;:projection projektio
                                    ;:extent (clj->js estonian-extent)
                                    }
                                   (when current-zoom current-zoom)))))

    ;;(.log js/console "L.map = " ol3)
    (reagent/set-state this {:ol3             ol3
                             :geometry-layers {} ; key => vector layer
                             :hover           nil
                             :unmount-ch      unmount-ch})

    ;; If mapspec defines callbacks, bind them to ol3
    (aseta-klik-kasittelija this ol3
                            (:on-click mapspec)
                            (:on-select mapspec))
    (aseta-dblclick-kasittelija this ol3
                                (:on-dblclick mapspec)
                                (:on-dblclick-select mapspec))
    (aseta-hover-kasittelija this ol3)
    (aseta-drag-kasittelija this ol3 (:on-drag mapspec))
    (aseta-zoom-kasittelija this ol3 (:on-zoom mapspec))
    (aseta-postrender-kasittelija this ol3 (:on-postrender mapspec))

    (update-ol3-geometries this (:geometries mapspec))

    (when-let [mount (:on-mount mapspec)]
      (mount (laske-kartan-alue ol3)))))

(defn ol3-will-unmount [this]
  (let [{:keys [unmount-ch]} (reagent/state this)]
    (async/close! unmount-ch)
    (remove-window-resize-listener)))

(defn- ol3-did-update [this _]
  (on-container-resize this))


(defn- ol3-render [mapspec]
  (let [c (reagent/current-component)]
    [:div {:style {:position         "relative"
                   :width            "100%"
                   :height           "100%"
                   :background-color "white";(colors/map-bg)
                   :user-select      "none"}}
     [:div {:id    (:id mapspec)
            :data-cy "map-view"
            :class (:class mapspec)
            :style (merge {:width  (:width mapspec)
                           :height (:height mapspec)}
                          (:style mapspec))}]
     (when-let [piirra-tooltip? (:tooltip-fn mapspec)]
       (when-let [hover (-> c reagent/state :hover)]
         (go (<! (timeout tooltipin-aika))
             (when (= hover (:hover (reagent/state c)))
               (reagent/set-state c {:hover nil})))
         (when-let [tooltipin-sisalto
                    (or (piirra-tooltip? hover)
                        (some-> (:tooltip hover) (constantly)))]
           [:div {:style (merge #_style-base/shadow {:position "absolute"
                          :background-color "white"
                          :border-radius "3px"
                          :padding "5px"
                          :left (+ 20 (:x hover)) :top (+ 10 (:y hover))})}
            (tooltipin-sisalto)])))]))

(defn- update-ol3-geometries [component geometries]
  "Update the ol3 layers based on the data, mutates the ol3 map object."
  (let [{:keys [ol3 geometry-layers]} (reagent/state component)]

    ;; Remove any layers that are no longer present
    (doseq [[key [layer _]] geometry-layers
            :when (nil? (get geometries key))]
      (log/debug "POISTETAAN KARTTATASO " (name key) " => " layer)
      (.removeLayer ol3 layer))

    ;; For each current layer, update layer geometries
    (loop [new-geometry-layers {}
           [layer & layers] (keys geometries)]
      (if-not layer
        (do
          (log/info "NEW-GEOMETRY-LAYERS: " new-geometry-layers)
          (reagent/set-state component {:geometry-layers new-geometry-layers
                                        :geometries geometries}))
        (if-let [taso (get geometries layer)]
          (recur (assoc new-geometry-layers
                        layer (apply taso/paivita
                                     taso ol3
                                     (get geometry-layers layer)))
                 layers)
          (recur new-geometry-layers layers))))))

(defn- ol3-will-receive-props [this [_ mapspec]]
  (update-ol3-geometries this (:geometries mapspec))
  (orientate-map this mapspec))

;;;;;;;;;
;; The OpenLayers 3 Reagent component.

(defn openlayers [mapspec]
  "A OpenLayers map component."
  (reagent/create-class
    {:display-name "openlayers"
     :get-initial-state (fn [_] {:mapspec mapspec})
     :component-did-mount (fn [this] (ol3-did-mount this (:current-zoom mapspec)))
     :reagent-render ol3-render
     :component-will-unmount ol3-will-unmount
     :component-did-update ol3-did-update
     :component-will-receive-props ol3-will-receive-props}))


(defn event-map
  "Return the OpenLayers map that fired the given event."
  [^ol.MapEvent e]
  (.-map e))

(defn map-zoom
  "Return the current zoom level of the given OpenLayers map."
  [^ol.Map m]
  (-> m .getView .getZoom))

(defn map-resolution
  "Return the current extent of the given OpenLayers map."
  [^ol.Map m]
  (.getResolution (.getView m)))
