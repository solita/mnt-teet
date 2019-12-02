(ns teet.map.map-view
  "Common map view"
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [teet.map.openlayers :as openlayers]
            [teet.log :as log]
            [teet.localization :refer [tr]]
            [teet.map.map-controller :as map-controller]
            [teet.ui.container :as container]
            [teet.ui.material-ui :refer [Fab Button Switch Checkbox FormControlLabel Collapse ClickAwayListener Fade IconButton]]
            [teet.ui.typography :as typography]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.common.common-controller :as common-controller]
            [teet.map.map-styles :as map-styles]))

(def default-extent [20 50 30 60])

;; Center of Estonia in EPSG:3857
;(def default-center [2876047.9017341174, 8124120.910366586])

;; Center of estonian coordinate system
(def default-center [516493.16 6513417.97])

(defn category-layers-control
  [e! [category _layers] _map-controls]
  (let [toggle-collapse (fn [_]
                          (e! (map-controller/->ToggleCategoryCollapse category)))]
    (fn [e! [category layers] map-controls]
      (let [closing? (map-controller/atleast-one-open? layers)
            on-toggle toggle-collapse
            open? (get-in map-controls [category :collapsed?])
            side-component [Button
                            {:variant :text
                             :on-click #(e! (map-controller/->ToggleCategorySelect category true))}
                            (if closing?
                              (tr [:map :clear-selections])
                              (tr [:map :select-all]))]]
        [container/collapsible-container {:on-toggle on-toggle
                                          :open? open?
                                          :side-component side-component}
         (if (empty? category)
           "No category name in data"
           category)
         [itemlist/checkbox-list
          (for [[layer open?] layers]
            {:checked? open?
             :value layer
             :on-change (fn [e]
                          (.stopPropagation e)
                          (e! (map-controller/->LayerToggle category layer)))})]]))))

(defn map-layer-controls
  [e! _map-layers {:keys [_open?] :as _map-controls}]
  (r/create-class
    {:component-did-mount
     (fn [_]
       (e! (map-controller/->FetchMapLayers)))
     :reagent-render
     (fn [e! map-layers {:keys [open?] :as map-controls}]
       [:div
        [Fade {:in open?}
         [:div {:class (<class map-styles/map-controls)}
          [:div {:class (<class map-styles/map-controls-heading)}
           [typography/Heading3
            (tr [:map :map-layers])]
           [IconButton {:color :primary
                        :size :small
                        :on-click #(e! (map-controller/->CloseMapControls))}
            [icons/navigation-close]]]
          [:div
           [category-layers-control e!
            ["Katastri" {"katastriyksus"
                         (boolean (get-in map-layers ["Katastri" "katastriyksus"]))}]
            map-controls]
           (doall
             (for [[category _ :as layer] map-layers
                   :when (not= category "Katastri")]
               ^{:key category}
               [category-layers-control e! layer map-controls]))]]]])}))

(defn map-control-buttons [e! {:keys [background-layer map-controls map-restrictions] :as map-data
                               :or   {background-layer "kaart"}}
                           layer-controls?]
  [:div {:class (<class map-styles/map-control-buttons)}
   (when layer-controls?
     [Fab (merge
            {:size     :small
             :class    (<class map-styles/map-control-button)
             :on-click #(e! (map-controller/->ToggleMapControls))}
            (when (:open? map-controls)
              {:color :primary}))
      [icons/maps-layers]])
   [Fab (merge
          {:size     :small
           :class    (<class map-styles/map-control-button)
           :on-click (e! map-controller/->SetBackgroundLayer
                         (case background-layer
                           "kaart" "foto"
                           "foto" "kaart"))}
          (when (= background-layer "foto")
            {:color :primary}))
    [icons/maps-satellite]]])

(defn map-container-style
  []
  {:display        :flex
   :flex-direction :column
   :flex           1
   :position       :relative
   :overflow       :hidden})

(defn map-view [e! {:keys [height class layer-controls?] :or {height "100%"} :as opts}
                {:keys [map-restrictions map-controls background-layer] :as map-data
                 :or   {background-layer "kaart"}}]
  (r/with-let [current-tool (volatile! (get-in map-data [:tool]))
               current-zoom (volatile! nil)
               current-res (volatile! nil)
               on-zoom (volatile! nil)]

    (vreset! current-tool (get-in map-data [:tool]))
    (vreset! on-zoom (get-in map-data [:on-zoom]))

    (let [{:keys [extent]} map-data]
      [:div {:class (<class map-container-style)}
       [map-layer-controls e! map-restrictions map-controls]
       [map-control-buttons e! map-data layer-controls?]

       ;; FIXME: background layer type added as key to the openlayers component so that it is forced
       ;; to rerender when it changes.
       ;; Proper fix is to update the background layers when they change, currently they are only
       ;; created once when mounting the component.
       ^{:key background-layer}
       [openlayers/openlayers
        {:id                 "mapview"
         :width              "100%"
         ;; set width/height as CSS units, must set height as pixels!
         :height             height
         :unselectable       "on"
         :style              (merge {:user-select "none"
                                     :display     :flex}
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
                                      :content    [:div {:class (<class map-styles/map-overlay)}
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

         ;; Show "foto" or "kaart" background layer from Maa-amet
         :layers             [{:type :maa-amet :layer background-layer :default true}]
         #_(vec
             (for [layer ["BAASKAART" "MAANTEED" "pohi_vr2"]]
               {:type    :wms :url "http://kaart.maaamet.ee/wms/alus?"
                :layer   layer
                :style   ""
                :default true}))}]])))
