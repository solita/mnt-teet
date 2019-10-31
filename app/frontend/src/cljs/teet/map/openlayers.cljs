(ns teet.map.openlayers
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
            [ol.interaction :as ol-interaction]
            [ol.interaction.Select]
            [ol.layer.Layer]
            [ol.layer.Vector]
            [ol.source.Vector]

            [reagent.core :as reagent :refer [atom]]

            ;[teet.geo :as geo]
            ;[teet.style.base :as style-base]
            ;[teet.style.colors :as colors]
            [teet.map.openlayers.kuvataso :as kuvataso]
            [teet.map.openlayers.projektiot :refer [estonian-extent]]
            [teet.map.openlayers.layer :as taso]
            [teet.map.openlayers.background :as background]

            [teet.log :as log]

            [tuck.effect])

  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; EPSG:3857 = WGS 84 / Pseudo Mercator -- Spherical Mercator
;; Used by google maps, open street map
;;
;; EPSG:3301 = estonian coordinate system 1997
(def default-projection "EPSG:3301")


(def ^{:doc "Initial resolution of ol3 view" :const true}
  initial-resolution 1200)

(def ^{:doc "Smallest possible zoom level allowed for the user to zoom out"
       :const true}
  min-zoom 0)
(def ^{:doc "Largest possible zoom level allowed for the user to zoom in"
       :const true}
  max-zoom 21)


(def tooltip-time-ms 3000)

^{:doc "Set extent buffer to avoid zooming too close"}
(def extent-buffer-default 1000)

;; Atom for active map interactions, so removing them is easier
(defonce map-interactions (cljs.core/atom {}))


;; These atoms can be used to set custom handlers for map clicks and
;; hovers. If set, they override the default behavior. Note that hese
;; are normal cljs atoms, not reagent ones.
(defonce click-handler (cljs.core/atom []))
(defonce hover-handler (cljs.core/atom []))

(defn set-click-handler!
  "Sets a click handler for the map. Returns a function for removing the
  handler. Multiple handlers can coexist, but only the most recently
  added will be called."
  [f]
  (swap! click-handler conj f)
  #(swap! click-handler (fn [handlers]
                             (filterv (partial not= f) handlers))))

(defn set-hover-handler!
  "Sets a hover handler for the map. Returns a function for removing the
  handler. Multiple handlers can coexist, but only the most recently
  added will be called."
  [f]
  (swap! hover-handler conj f)
  #(swap! hover-handler (fn [handlers]
                              (filterv (partial not= f) handlers))))

;; A channel for map commands
(def command-ch (chan))

(defn show-popup! [lat-lng content]
  (go (>! command-ch [::popup lat-lng content])))

(defn hide-popup! []
  (go (>! command-ch [::hide-popup])))

(defn hide-popup-without-event! []
  (go (>! command-ch [::hide-popup-without-event])))

(defn invalidate-size! []
  (go (>! command-ch [::invalidate-size])))

(defn set-cursor! [kursori]
  (go (>! command-ch [::cursor kursori])))

(defn set-tooltip! [x y teksti]
  (go (>! command-ch [::tooltip x y teksti])))

(defn fit!
  "Fit map view to given extent or geometry.
  If argument is a vector it is interpreted to be an extent [xmin ymin xmax ymax].
  Otherwise argument should be OpenLayers JS extent or geometry object."
  [extent-or-geometry]
  (let [to (if (vector? extent-or-geometry)
             (clj->js extent-or-geometry)
             extent-or-geometry)]
    (go (>! command-ch [::fit to]))))

;;;;;;;;;
;; Define the React lifecycle callbacks to manage the OpenLayers
;; Javascript objects.

(declare update-ol3-geometries update-ol3-overlays)


(def ^:export the-map (atom nil))

(defmethod tuck.effect/process-effect :refresh-layer [_ {layer-name :layer}]
  (when-let [layer (some #(when (= (.get % "teet-layer-name") (name layer-name)) %)
                         (.getArray (.getLayers @the-map)))]
    (log/info "Refreshing layer " layer)
    (.clear (.getSource layer))
    (.refresh (.getSource layer))))

;; expose map to browser for development and debugging
(defn ^:export get-the-map [] @the-map)

(defn set-map-size! [w h]
  (.setSize @the-map (clj->js [w h])))

(defn center-map-on-point! [center]
  (when-let [ol3 @the-map]
    (.setCenter (.getView ol3) (clj->js center))))

(defn center-map-on-area! [area]
  (assert (vector? area) "Area must be a vector of numbers")
  (assert (= 4 (count area)) "Area must be a vector [minx miny maxx maxy]")
  (when-let [ol3 @the-map]
    (let [view (.getView ol3)]
      (.fit view (clj->js area) (.getSize ol3)))))

(defn extent-contains-extent? [large small]
  (assert (and (vector? large) (vector? small)) "Area must be a vector of numbers")
  (assert (and (= 4 (count large)) (= 4 (count small)))
          "Area must be a vector [minx miny maxx maxy]")

  (ol/extent.containsExtent (clj->js large) (clj->js small)))

(defn ^:export debug-center [x y]
  (center-map-on-point! [x y]))

(defn ^:export invalidate-size []
  (.invalidateSize @the-map))

(defn map-extent []
  (when-let [k @the-map]
    (.calculateExtent (.getView k) (.getSize k))))

(defonce openlayers-map-width (atom nil))
(defonce openlayers-map-height (atom nil))

(defn same-layer? [old new]
  (kuvataso/sama? old new))

(defn center-point
  "Calculates the center point of the geometry based on the extent"
  [geometria]
  (let [[x1 y1 x2 y2] (.getExtent geometria)]
    [(+ x1 (/ (- x2 x1) 2))
     (+ y1 (/ (- y2 y1) 2))]))

(defn feature-geometry [feature layer]
  (or
   ;; Normal geometry layer item
   (.get feature "teet-geometria")

   ;; Background layer item (from vector tile)
   {:map/tooltip (.get feature "tooltip")
    :map/type (.get layer "teet-source")
    :map/id (.get feature "id")}))

(defn- event-geometry
  "Obtains geometry for the given ol3 event. Returns the first geometry found."
  ([this e] (event-geometry this e true))
  ([this e return-first?]
   (let [geom (volatile! [])
         {:keys [ol3]} (reagent/state this)]
     (.forEachFeatureAtPixel ol3 (.-pixel e)
                             (fn [feature layer]
                               (vswap! geom conj (feature-geometry feature layer))
                               return-first?)
                             ;; Function can be given options, which contians hitTolerance. Meaning the radius, from which features are fetched.
                             )

     (cond
       (empty? @geom)
       nil

       return-first?
       (first @geom)

       :else @geom))))

(defn- get-map-area [ol3]
  (.calculateExtent (.getView ol3) (.getSize ol3)))

(defn- event-description
  "Event description for external handlers"
  [this e]
  (let [c (.-coordinate e)
        type (.-type e)]
    {:type     (case type
                 "pointermove" :hover
                 "click" :click
                 "singleclick" :click
                 "dblclick" :dbl-click)
     :geometry (event-geometry this e)
     :location [(aget c 0) (aget c 1)]
     :x        (aget (.-pixel e) 0)
     :y        (aget (.-pixel e) 1)}))

(defn- set-postrender-handler [_ ol3 on-postrender]
  (.on ol3 "postrender"
       (fn [e]
         (when on-postrender
           (on-postrender e)))))

(defn- set-zoom-handler [_ ol3 on-zoom]
  (.on (.getView ol3) "change:resolution"
       (fn [e]
         (when on-zoom
           (on-zoom e (get-map-area ol3))))))

(defn- set-drag-handler [_ ol3 on-move]
  (.on ol3 "pointerdrag" (fn [e]
                           (when on-move
                             (on-move e (get-map-area ol3))))))

(defn- set-click-handler [this ol3 on-click on-select]
  (.on ol3 "singleclick"
       (fn [e]
         (if-let [kasittelija (peek @click-handler)]
           (kasittelija (event-description this e))

           (if-let [g (event-geometry this e true)]
             (do
               (log/info "on-select" g)
               (when on-select (on-select [g] e)))
             (do
               (log/info "on-click" e)
               (aset js/window "EVT" e)
               (when on-click (on-click e))))))))

(defn- set-dblclick-handler [this ol3 _on-click on-select]
  (.on ol3 "dblclick"
       (fn [e]
         (if-let [handler (peek @click-handler)]
           (handler (event-description this e))
           (when on-select
             (when-let [g (event-geometry this e false)]
               (on-select g e)))))))

(defn set-hover-handler [this target ol3]
  (.on ol3 "pointermove"
       (fn [e]
         (if-let [handler (peek @hover-handler)]
           (handler (event-description this e))

           (let [g (event-geometry this e)
                 map-element-style (-> target js/document.getElementById .-style)]
             (reagent/set-state this (if g
                                       {:hover (assoc g
                                                      :x (aget (.-pixel e) 0)
                                                      :y (aget (.-pixel e) 1))}
                                       {:hover nil}))
             (set! (.-cursor map-element-style) (if g "pointer" "")))))))


(defn center!
  "Centers the map view to fit the given feature"
  [ol3 feature]
  (let [view (.getView ol3)
        extent (.getExtent (.getGeometry feature))]
    (.fit view extent (.getSize ol3))))

(defn- remove-openlayers-popup!
  "Do not call this directly. Istead, use `remove-popup!` or `remove-popup-without-event!`"
  [this]
  (let [{:keys [ol3 popup]} (reagent/state this)]
    (when popup
      (.removeOverlay ol3 popup)
      (reagent/set-state this {:popup nil}))))

(defn- remove-popup!
  "Removes map popup if it exists."
  [this]
  #_(t/julkaise! {:aihe :popup-suljettu}) ;; TODO Why is this commented out?
  (remove-openlayers-popup! this))

(defn- remove-popup-without-event!
  "Removes map popup if it exists without publishing event."
  [this]
  (remove-openlayers-popup! this))

(defn create-overlay [coordinates contents]
  (let [elt (js/document.createElement "span")]
    (reagent/render contents elt)
    (ol.Overlay. (clj->js {:element   elt
                           :position  coordinates
                           :stopEvent false}))))


(defn- display-popup!
  "Displays the given popup contents in the given coordinates. If a
  previous popup exists, it is removed."
  [this coordinates contents]
  (let [{:keys [ol3 popup]} (reagent/state this)]
    (when popup
      (.removeOverlay ol3 popup))
    (let [popup (create-overlay coordinates
                 [:div.ol-popup
                  [:a.ol-popup-closer.klikattava
                   {:on-click #(do (.stopPropagation %)
                                   (.preventDefault %)
                                   (remove-popup! this))}]
                  contents])]
      (.addOverlay ol3 popup)
      (reagent/set-state this {:popup popup}))))

(defn current-zoom-level []
  (some-> @the-map (.getView) (.getZoom)))

(defn set-zoom [zoom & [duration]]
  (some-> @the-map (.getView) (.setZoom #js {:zoom zoom
                                             :duration (or duration 250)})))

(defn animate-zoom [zoom & [duration]]
  (some-> @the-map (.getView) (.animate #js {:zoom zoom
                                             :duration (or duration 250)})))

(defn extent-center [[minx miny maxx maxy]]
  (let [width (- maxx minx)
        height (- maxy miny)]
    [(+ minx (/ width 2))
     (+ miny (/ height 2))]))


(defn- create-dragbox-interaction [opts]
  (ol-interaction/DragBox. (clj->js opts)))

(defn- create-select-interaction [opts]
  (ol-interaction/Select. (clj->js opts)))


(defn add-interaction! [interaction key]
  (some-> @the-map (.addInteraction interaction))
  (swap! map-interactions assoc key interaction))

(defn remove-interaction! [key]
  (some-> @the-map (.removeInteraction (key @map-interactions)))
  (swap! map-interactions dissoc key))


(defn enable-bbox-select! [_]
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

(defonce window-resize-listener-atom (atom nil))

(defn- on-container-resize [this]
  (let [new-width (.-offsetWidth
                      (aget (.-childNodes (reagent/dom-node this)) 0))
        new-height (.-offsetHeight
                       (aget (.-childNodes (reagent/dom-node this)) 0))]

    (when-not (and (= new-width
                      @openlayers-map-width)
                   (= new-height
                      @openlayers-map-height))
      (reset! openlayers-map-width new-width)
      (reset! openlayers-map-height new-height)

      (invalidate-size!))))



(defn- remove-window-resize-listener []
  (when @window-resize-listener-atom
    (.removeEventListener js/window "resize" @window-resize-listener-atom)))

(defn- set-window-resize-listener [this]
  (remove-window-resize-listener)
  (reset! window-resize-listener-atom (partial on-container-resize this))
  (.addEventListener js/window "resize" @window-resize-listener-atom))


(defn- ol3-did-mount
  "Initialize OpenLayers map for a newly mounted map component."
  [this current-zoom]
  (let [{layers :layers :as mapspec} (:mapspec (reagent/state this))
        interactions (let [defaults (ol-interaction/defaults
                                     #js {:mouseWheelZoom true
                                          :dragPan        false})]
                      ;; No `kinetic` property!
                      (.push defaults (ol-interaction/DragPan. #js {}))
                      defaults)

        ;; Re-add later added interactions (if there are any) on re-mount
        _ (doseq [interaction (vals @map-interactions)]
            (.push interactions interaction))

        ;; NOTE: Currently disabled, because implement our own map control tools
        ;; kontrollit (ol-control/defaults #js {})

        map-options (clj->js {:layers       (mapv background/create-background-layer layers)
                             :target       (:id mapspec)
                             :controls [] ;; :controls     kontrollit
                             :interactions interactions})
        ol3 (ol/Map. map-options)

        _ (reset!
            openlayers-map-width
            (.-offsetWidth (aget (.-childNodes (reagent/dom-node this)) 0)))

        _ (reset!
            openlayers-map-height
            (.-offsetHeight (aget (.-childNodes (reagent/dom-node this)) 0)))

        _ (reset! the-map ol3)

        _ (set-window-resize-listener this)



        extent (:extent mapspec)
        center (:center mapspec)
        unmount-ch (chan)]


    ;; Begin listening to command channel
    (go-loop [[[command & args] ch] (alts! [command-ch unmount-ch])]
             (when-not (= ch unmount-ch)
               (case command

                 ::popup
                 (let [[coordinate content] args]
                   (display-popup! this coordinate content))

                 ::invalidate-size
                 (do
                   (.updateSize ol3)
                   (.render ol3))

                 ::hide-popup
                 (remove-popup! this)

                 ::hide-popup-without-event
                 (remove-popup-without-event! this)

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
                                      {:hover {:x x :y y :tooltip teksti}}))

                 ::fit
                 (let [[extent-or-geometry] args]
                   (.fit (.getView ol3) extent-or-geometry)))

               (recur (alts! [command-ch unmount-ch]))))


    (.setView
     ol3 (ol.View. (clj->js (merge {:center (clj->js (or center (extent-center extent)))
                                    :resolution initial-resolution
                                    :maxZoom max-zoom
                                    :minZoom min-zoom
                                    :projection default-projection
                                    :extent (clj->js estonian-extent)
                                    }
                                   (when current-zoom current-zoom)))))

    ;;(.log js/console "L.map = " ol3)
    (reagent/set-state this {:ol3             ol3
                             :geometry-layers {} ; key => vector layer
                             :hover           nil
                             :unmount-ch      unmount-ch})

    ;; If mapspec defines callbacks, bind them to ol3
    (set-click-handler this ol3
                       (:on-click mapspec)
                       (:on-select mapspec))
    (set-dblclick-handler this ol3
                          (:on-dblclick mapspec)
                          (:on-dblclick-select mapspec))
    (set-hover-handler this (:id mapspec) ol3)
    (set-drag-handler this ol3 (:on-drag mapspec))
    (set-zoom-handler this ol3 (:on-zoom mapspec))
    (set-postrender-handler this ol3 (:on-postrender mapspec))

    (update-ol3-geometries this (:geometries mapspec))
    (update-ol3-overlays this (:overlays mapspec))

    (when-let [mount (:on-mount mapspec)]
      (mount (get-map-area ol3)))))

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
     (when-let [draw-tooltip? (:tooltip-fn mapspec)]
       (when-let [hover (-> c reagent/state :hover)]
         (go (<! (timeout tooltip-time-ms))
             (when (= hover (:hover (reagent/state c)))
               (reagent/set-state c {:hover nil})))
         (when-let [tooltip-content
                    (or (draw-tooltip? hover)
                        (some-> (:tooltip hover) (constantly)))]
           [:div {:style (merge #_style-base/shadow {:position "absolute"
                          :background-color "white"
                          :border-radius "3px"
                          :padding "5px"
                          :left (+ 20 (:x hover)) :top (+ 10 (:y hover))})}
            (tooltip-content)])))]))

(defn- update-ol3-geometries
  "Update the ol3 layers based on the data, mutates the ol3 map object."
  [component geometries]
  (let [{:keys [ol3 geometry-layers]} (reagent/state component)]

    ;; Remove any layers that are no longer present
    (doseq [[key [layer _]] geometry-layers
            :when (nil? (get geometries key))]
      (log/debug "REMOVING MAP LAYER " (name key) " => " layer)
      (.removeLayer ol3 layer))

    ;; For each current layer, update layer geometries
    (loop [new-geometry-layers {}
           [layer & layers] (keys geometries)]
      (if-not layer
        (reagent/set-state component {:geometry-layers new-geometry-layers
                                      :geometries geometries})
        (if-let [taso (get geometries layer)]
          (recur (assoc new-geometry-layers
                        layer (apply taso/paivita
                                     taso ol3
                                     (get geometry-layers layer)))
                 layers)
          (recur new-geometry-layers layers))))))

(defn- update-ol3-overlays
  "Update map overlays based on the data, mutates the ol3 map object."
  [component overlays]
  (let [{ol3 :ol3
         previous-overlays :overlays} (reagent/state component)
        overlays-set (into #{} overlays)]

    ;; Remove overlays not present in new data
    (doseq [[ov overlay] previous-overlays
            :when (not (overlays-set ov))]
      (.removeOverlay ol3 overlay))

    (reagent/set-state
     component
     {:overlays
      (into {}
            (for [{:keys [coordinate content] :as ov} overlays
                  :when (not (contains? previous-overlays ov))
                  :let [overlay (create-overlay coordinate content)]]
              (do
                (log/info "ADD OVERLAY TO " coordinate " with " content)
                (.addOverlay ol3 overlay)
                [ov overlay])))})))

(defn- ol3-will-receive-props [this [_ mapspec]]
  (update-ol3-geometries this (:geometries mapspec))
  (update-ol3-overlays this (:overlays mapspec)))

;;;;;;;;;
;; The OpenLayers 3 Reagent component.

(defn openlayers
  "A OpenLayers map component."
  [mapspec]
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
