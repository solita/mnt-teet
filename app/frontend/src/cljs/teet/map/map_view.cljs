(ns teet.map.map-view
  "Common map view"
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [teet.map.openlayers :as openlayers]
            [taoensso.timbre :as log]
            [teet.map.map-controller :as map-controller]
            [teet.ui.material-ui :refer [Fab Button Switch FormControlLabel Collapse ClickAwayListener]]
            [teet.ui.typography :as typography]
            [teet.ui.icons :as icons]
            [teet.common.common-controller :as common-controller]
            [teet.map.map-styles :as map-styles]))

(def default-extent [20 50 30 60])

;; Center of Estonia in EPSG:3857
;(def default-center [2876047.9017341174, 8124120.910366586])

;; Center of estonian coordinate system
(def default-center [516493.16 6513417.97])

(defn restriction-control
  [e! [category layers] _map-controls]
  (let [toggle-collapse (fn [_]
                          (e! (map-controller/->ToggleCategoryCollapse category)))
        closing? (map-controller/atleast-one-open? layers)]
    (fn [e! [category layers] map-controls]
      [:div
       [:div {:style {:display         :flex
                      :align-items     :center
                      :justify-content :space-between}}

        [Button {:on-click toggle-collapse}
         (if (get-in map-controls [category :collapsed?])
           [icons/navigation-arrow-drop-up]
           [icons/navigation-arrow-drop-down])]
        [:h3 {:style {:margin 0
                      :flex   1}} category]
        [Button
         {:on-click #(e! (map-controller/->ToggleCategorySelect category true))}
         (if closing?
           "Clear selections"                               ;;TODO: check that tthis implementation is ok and then add translations for these
           "Select all")]]
       [Collapse {:in    (get-in map-controls [category :collapsed?])
                  :style {:padding "0 0.5rem"}}
        [:div
         (doall
           (for [[layer open?] layers]
             ^{:key layer}
             [:div {:style {:margin-left "1rem"}}
              [FormControlLabel
               {:label   layer
                :control (r/as-component [Switch {:checked   open?
                                                  :value     layer
                                                  :on-change (fn [e]
                                                               (.stopPropagation e)
                                                               (e! (map-controller/->LayerToggle category layer)))}])}]]))]]])))

(defn map-layer-controls
  [e! _map-layers {:keys [_open?] :as _map-controls}]
  (r/create-class
    {:component-did-mount
     (fn [_]
       (e! (map-controller/->FetchMapLayers)))
     :reagent-render
     (fn [e! map-layers {:keys [open?] :as map-controls}]
       [:div
        (when open?
          [ClickAwayListener {:on-click-away #(e! (map-controller/->CloseMapControls))}
           [:div {:class (<class map-styles/map-controls)}
            [typography/Heading3 {:class (<class map-styles/map-controls-heading)}
             "Map layers"]
            [:div {:class (<class map-styles/layer-container)}
             (doall
               (for [layer map-layers]
                 ^{:key [layer]}
                 [restriction-control e! layer map-controls]))]]])
        [:div {:class (<class map-styles/map-controls-button)}
         [Fab {:on-click #(e! (map-controller/->ToggleMapControls))}
          [icons/maps-layers]]]])}))

(defn map-view [e! {:keys [height class] :or {height "100%"} :as opts} {:keys [map-restrictions map-controls] :as map-data}]
  (r/with-let [current-tool (volatile! (get-in map-data [:tool]))
               current-zoom (volatile! nil)
               current-res (volatile! nil)
               on-zoom (volatile! nil)]

              (vreset! current-tool (get-in map-data [:tool]))
              (vreset! on-zoom (get-in map-data [:on-zoom]))

              (let [{:keys [extent]} map-data]
                [:div {:style {:position :relative}}
                 [map-layer-controls e! map-restrictions map-controls]
                 [openlayers/openlayers
                  {:id                 "mapview"
                   :width              "100%"
                   ;; set width/height as CSS units, must set height as pixels!
                   :height             height
                   :unselectable       "on"
                   :style              (merge {:user-select "none"}
                                              (when (#{:bbox-select :position-select} @current-tool)
                                                {:cursor "crosshair"}))
                   :class              class
                   :extent             (or extent default-extent)
                   :center             default-center

                   ;:selection          nav/valittu-hallintayksikko
                   :on-drag            (fn [_item _event]
                                         #_(log/debug "drag" item event)
                                         #_(paivita-extent item event)
                                         #_(t/julkaise! {:aihe :karttaa-vedetty}))
                   :on-postrender      (fn [e]
                                         (let [old-z @current-zoom
                                               new-z (some->> e openlayers/event-map openlayers/map-zoom js/Math.round)
                                               new-res (some->> e openlayers/event-map openlayers/map-resolution)]
                                           (when (not= old-z new-z)
                                             (vreset! current-zoom new-z)
                                             (vreset! current-res new-res)
                                             #_(e! (map-controller/->UpdateMapInfo {:zoom        new-z
                                                                                    :is-zooming? true
                                                                                    :resolution  new-res}))

                                             #_(doseq [on-zoom @on-zoom
                                                       :let [e (common-controller/on-zoom on-zoom new-z)]
                                                       :when e]
                                                 (e! e))
                                             #_(e! (map-controller/->ResetIsZooming)))))
                   :on-mount           (fn [initialextent]
                                         (log/debug "on-mount" initialextent)
                                         #_(paivita-extent nil initialextent)
                                         #_(e! (map-controller/->UpdateMapLayers)))
                   :on-click           (fn [event]
                                         (when-let [on-click (:on-click opts)]
                                           (on-click {:coordinate (js->clj (aget event "coordinate"))})))

                   :on-select          (fn [[item & _] _event]
                                         (when-let [event (common-controller/map-item-selected item)]
                                           (e! event)))

                   :on-dblclick        nil

                   :on-dblclick-select nil #_kasittele-dblclick-select!

                   :tooltip-fn         (fn [geom]
                                         (when-let [tt (:map/tooltip geom)]
                                           ;; Returns a function for current tooltip value or nil
                                           ;; if item has no tooltip specified.
                                           (constantly [:div tt])))

                   :geometries         (merge (get-in map-data [:geometries])
                                              (get-in map-data [:layers])
                                              (:layers opts))

                   :overlays           (mapv (fn [{:keys [coordinate content]}]
                                               {:coordinate coordinate
                                                :content [:div {:class (<class map-styles/map-overlay)}
                                                          content]})
                                             (:overlays opts))
                   :current-zoom       (get-in map-data [:map-info])
                   ;; map of geometry layer keys to control map zoom. If a key's value changes map is re-zoomed
                   :zoom-to-geometries (get-in map-data [:zoom-to-geometries])

                   ;; name of layer that should be the center when zooming to geometries
                   :center-on-geometry (get-in map-data [:center-on-geometry])

                   ;; Use this to set a buffer for extent fitting on map view. If not defined, default value is used.
                   :extent-buffer      (get-in map-data [:extent-buffer])

                   :rotation           (if (get-in map-data [:rotate?])
                                         (get-in map-data [:rotation])
                                         0)

                   :layers             [{:type :maa-amet :layer "kaart" :default true}]
                   #_(vec
                       (for [layer ["BAASKAART" "MAANTEED" "pohi_vr2"]]
                         {:type    :wms :url "http://kaart.maaamet.ee/wms/alus?"
                          :layer   layer
                          :style   ""
                          :default true}))}]])))
