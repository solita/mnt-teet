(ns teet.map.map-view
  "Common map view"
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [teet.map.openlayers :as openlayers]
            [teet.log :as log]
            [teet.localization :refer [tr]]
            [teet.map.map-controller :as map-controller]
            [teet.map.map-overlay :as map-overlay]
            [teet.ui.container :as container]
            [teet.ui.material-ui :refer [Fab Button Grid Checkbox]]
            [teet.ui.typography :as typography]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.common.common-controller :as common-controller]
            [teet.map.map-styles :as map-styles]
            [teet.ui.buttons :as buttons]
            [teet.ui.panels :as panels]
            [teet.map.map-layers :as map-layers]
            [teet.ui.util :as util :refer [mapc]]
            [teet.util.collection :as cu]
            [teet.ui.common :as common]
            [teet.ui.query :as query]
            [teet.ui.select :as select]
            [clojure.string :as str]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.form :as form]))

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
             :id layer
             :on-change (fn [e]
                          (.stopPropagation e)
                          (e! (map-controller/->LayerToggle category layer)))})]]))))
(def ^:private layer-types [:projects
                            :restrictions
                            :cadastral-units
                            :land-surveys
                            :teeregister
                            :eelis
                            :heritage
                            :heritage-protection-zones])


(defmulti layer-filters-form
  "Render layer type speficic filters selection form."
  (fn [_e! layer _map-data] (:type layer)))

(defmethod layer-filters-form :restrictions
  [e! {checked-ids :datasource-ids :as layer
       :or {checked-ids #{}}} map-data]
  [:div
   (util/with-keys
     (for [{:keys [id description] :as ds} (:datasources map-data)
           :when (map-controller/restriction-datasource? ds)
           :let [checked? (boolean (checked-ids id))
                 toggle (e! map-controller/->UpdateLayer
                            (update layer :datasource-ids cu/toggle id))]]
       [:div {:on-click toggle
              :style {:cursor :pointer}}
        [Checkbox {:checked checked?
                   :on-change toggle}]
        description]))])

(defmethod layer-filters-form :cadastral-units
  [e! _ map-data]
  ;; immediately set the one datasource id
  (e! (map-controller/->UpdateLayer {:datasource-ids
                                     (into #{}
                                           (comp
                                            (filter #(= "cadastral-units" (:name %)))
                                            (map :id))
                                           (:datasources map-data))}))
  (fn [_ _ _]
    (tr [:map :layers :no-filters])))

(defn- wms-layer-selector [toggle-layer! selected layers]
  [:div
   (doall
    (for [{:keys [name title layers] :as layer} layers
          :let [selected? (contains? selected name)]]
      ^{:key name}
      [:div
       (if (seq layers)
         [:div
          [:b title]
          [:div {:style {:margin-left "1rem"}}
           [wms-layer-selector toggle-layer! selected layers]]]

         [select/checkbox {:value selected?
                           :on-change #(toggle-layer! layer)
                           :label title}])]))])

(defn- wms-layer-selector* [e! layer-opts {:keys [wms-url layers]}]
  (let [selected (or (:selected layer-opts) #{})]
    [wms-layer-selector
     #(e! (map-controller/->UpdateLayer
           {:wms-url wms-url
            :layer-info (assoc (:layer-info layer-opts)
                               (:name %) (select-keys % [:title :legend-url]))
            :selected (cu/toggle selected (:name %))}))
     selected
     layers]))

(defmethod layer-filters-form :teeregister
  [e! layer map-data]
  [query/query {:e! e!
                :args {}
                :state-path [:map :teeregister-layers]
                :state (:teeregister-layers map-data)
                :query :road/wms-layers
                :simple-view [wms-layer-selector* e! layer]}])

(defmethod layer-filters-form :eelis
  [e! layer map-data]
  [query/query {:e! e!
                :args {}
                :state-path [:map :eelis-layers]
                :state (:eelis-layers map-data)
                :query :road/eelis-wms-layers
                :simple-view [wms-layer-selector* e! layer]}])

(defmethod layer-filters-form :default
  [_e! _layer _map-data]
  (tr [:map :layers :no-filters]))

(defmethod layer-filters-form :projects
  [e! layer _map-data]
  [form/form {:e! e!
              :on-change-event (e! map-controller/->UpdateLayer)
              :value layer}
   ^{:attribute :text :xs 6}
   [TextField {:label (tr [:fields :thk.project/project-name])}]

   ^{:attribute :road :xs 6}
   [TextField {:label (tr [:fields :thk.project/road-nr])
               :type :number}]

   ^{:attribute :region :xs 6}
   [TextField {:label (tr [:fields :thk.project/region-name])}]

   ^{:attribute :km :xs 6}
   [TextField {:label (tr [:project :information :km-range])
               :type :number}]

   ^{:attribute :date :xs 6}
   [date-picker/date-input {:label (tr [:fields :thk.project/estimated-date-range])}]

   ^{:attribute :owner :xs 6}
   [select/select-user {:label (tr [:fields :thk.project/owner])
                        :e! e!}]])

(defmethod layer-filters-form :default
  [_ _ _]
  (tr [:map :layers :no-filters]))

(defmulti layer-legend (fn [layer _map-data] (:type layer)))

(defn- wms-legend [{:keys [selected layer-info]}]
  [:div
   (doall
    (for [layer selected
          :let [{:keys [title legend-url]} (get layer-info layer)]
          :when legend-url]
      ^{:key layer}
      [:div
       [:div title]
       [:img {:src legend-url}]]))])

(defmethod layer-legend :teeregister
  [layer _]
  (wms-legend layer))

(defmethod layer-legend :eelis
  [layer _]
  (wms-legend layer))

(defmethod layer-legend :default
  [_ _]
  [:span])

(defn edit-layer-dialog [e! {:keys [new? type] :as edit-layer} map-data]
  [panels/modal {:disable-content-wrapper? true
                 :on-close (e! map-controller/->CancelLayer)}
   (let [edit-component
         [:div {:class (<class map-styles/edit-layer-form)}
          [typography/Heading2 {:class (<class map-styles/layer-heading-style)}
           (if type
             (str (tr [:map :layers type])
                  " "
                  (tr [:map :layers :layer]))
             "")]
          [:div {:class (<class map-styles/edit-layer-options)}
           (when type
             ^{:key (str type)}
             [layer-filters-form e! edit-layer map-data])]
          [:div.edit-layer-buttons {:class (<class map-styles/layer-edit-button-container-style)}
           (if new?
             [buttons/button-secondary {:on-click (e! map-controller/->CancelLayer)}
              (tr [:buttons :cancel])]

             [buttons/button-warning {:on-click (e! map-controller/->RemoveLayer edit-layer)}
              (tr [:buttons :delete])])
           [buttons/button-primary {:on-click (e! map-controller/->SaveLayer)
                                    :disabled (nil? type)
                                    :class (<class map-styles/layer-edit-save-style)}
            (tr [:buttons :save])]]]]
     (if new?
       [Grid {:container true}
        [Grid {:item true
               :xs 4}
         [:div {:class (<class map-styles/edit-layer-type)}
          (doall
           (for [type layer-types]
             ^{:key (name type)}
             [typography/Text {:class (<class map-styles/edit-layer-type-heading
                                              (= type (:type edit-layer)))
                                   :on-click (e! map-controller/->UpdateLayer {:type type})}
              (tr [:map :layers type])]))]]
        [Grid {:item true
               :xs 8}
         edit-component]]
       edit-component))])

(defn map-layer-controls
  [e! {:keys [layers edit-layer] :as map-data}]
  [:<>
   (when edit-layer
     [edit-layer-dialog e! edit-layer map-data])
   [:div {:class (<class map-styles/map-layer-controls)}
    [:div {:class (<class map-styles/map-controls-heading)}
     [icons/maps-layers]
     (tr [:map :map-layers])]
    [:div {:class (<class map-styles/map-layer-controls-body)}
     (if (seq layers)
            (util/with-keys
              (for [layer layers]
                ^{:key (or (:id layer) (:type layer))}
                [common/feature-and-action
                 {:label (tr [:map :layers (:type layer)])}
                 {:button-label (tr [:buttons :edit])
                  :action (e! map-controller/->EditLayer layer)}]))
            [:div (tr [:map :layers :no-layers])])
     [buttons/add-button
      {:on-click (e! map-controller/->AddLayer)
       :class (<class map-styles/add-layer-button)}
      [icons/content-add-circle]]]]])

(defn map-control-buttons [e! {:keys [background-layer layers] :as _map-data
                               :or   {background-layer ["kaart"]}}]
  [:div {:class (<class map-styles/map-control-buttons)}
   [Fab (merge
         {:size     :small
          :class    (<class map-styles/map-control-button)
          :on-click (e! map-controller/->SetBackgroundLayer
                        (case background-layer
                          ["kaart"] ["foto"]
                          ["foto"]
                          ["foto" "hybriid"] ["kaart"]))}
         (when (= background-layer "foto")
           {:color :primary}))
    [icons/maps-layers]]])

(defn map-container-style
  [full-height?]
  (merge
   {:display        :flex
    :flex-direction :column
    :flex           1
    :position       :relative
    :overflow       :hidden}
   (when full-height?
     {:height "100%"})))

(defn- create-data-layers [ctx layers]
  (log/info "Create data layers: " layers)
  (reduce merge {}
          (map-indexed
           (fn [i {:keys [id] :as layer}]
             (map-layers/create-data-layer
              ctx
              (merge {:id (or id (keyword (str "data-layer-" i)))}
                     layer)))
           layers)))

(defn map-view [e!
                {:keys [config height class layer-controls? allow-select?
                        full-height?]
                 :or {height "100%"
                      allow-select? true
                      full-height? false
                      } :as opts}
                {:keys [background-layer] :as map-data
                 :or   {background-layer ["kaart"]}}]
  (r/with-let [current-tool (volatile! (get-in map-data [:tool]))
               current-zoom (volatile! nil)
               current-res (volatile! nil)
               on-zoom (volatile! nil)]

    (vreset! current-tool (get-in map-data [:tool]))
    (vreset! on-zoom (get-in map-data [:on-zoom]))

    (let [{:keys [extent]} map-data]
      [:div {:class (<class map-container-style full-height?)}
       (when layer-controls?
         [map-layer-controls e! map-data])
       [map-control-buttons e! map-data]

       [openlayers/openlayers
        {:id "mapview"
         :width "100%"
         ;; set width/height as CSS units, must set height as pixels!
         :height height
         :unselectable "on"
         :style (merge {:user-select "none"
                        :display :flex}
                       (when (#{:bbox-select :position-select} @current-tool)
                         {:cursor "crosshair"}))
         :class class
         :extent (or extent default-extent)
         :center default-center
         :event-handlers (:event-handlers opts)
         :on-postrender (fn [e]
                          (let [old-z @current-zoom
                                new-z (some->> e openlayers/event-map openlayers/map-zoom js/Math.round)
                                new-res (some->> e openlayers/event-map openlayers/map-resolution)]
                            (when (not= old-z new-z)
                              (vreset! current-zoom new-z)
                              (vreset! current-res new-res)
                              #_(e! (map-controller/->UpdateMapInfo {:zoom new-z
                                                                     :is-zooming? true
                                                                     :resolution new-res}))

                              #_(doseq [on-zoom @on-zoom
                                        :let [e (common-controller/on-zoom on-zoom new-z)]
                                        :when e]
                                  (e! e))
                              #_(e! (map-controller/->ResetIsZooming)))))
         :on-mount (fn [initialextent]
                     (log/debug "on-mount" initialextent)
                     #_(paivita-extent nil initialextent)
                     #_(e! (map-controller/->UpdateMapLayers)))
         :on-click (fn [event]
                     (when-let [on-click (:on-click opts)]
                       (on-click {:coordinate (js->clj (aget event "coordinate"))})))

         :on-select (when allow-select?
                      (fn [[item & _] _event]
                        (when-let [event (common-controller/map-item-selected item)]
                          (e! event))))

         :on-dblclick nil

         :on-dblclick-select nil #_kasittele-dblclick-select!

         :tooltip-fn (fn [geom]
                       (aset js/window "TT" (:map/feature geom))
                       (let [feature-props (.getProperties (:map/feature geom))]
                         (when-let [tt (or (:map/tooltip geom)
                                           ;; fallback to checking "nimi" or "name" property
                                           (aget feature-props "nimi")
                                           (aget feature-props "name"))]
                           ;; Returns a function for current tooltip value or nil
                           ;; if item has no tooltip specified.
                           (constantly [:div (pr-str tt)]))))

         :geometries (merge (get-in map-data [:geometries])
                            (:layers opts)
                            (when layer-controls?
                              (create-data-layers {:config config}
                                                  (:layers map-data))))

         :overlays (mapv (fn [{:keys [coordinate content]}]
                           {:coordinate coordinate
                            :content [:div {:class (<class map-styles/map-overlay)}
                                      content]})
                         (concat
                          (when-let [overlay @map-overlay/selected-item-overlay]
                            [overlay])
                          (:overlays opts)))
         :current-zoom (get-in map-data [:map-info])
         ;; map of geometry layer keys to control map zoom. If a key's value changes map is re-zoomed
         :zoom-to-geometries (get-in map-data [:zoom-to-geometries])

         ;; name of layer that should be the center when zooming to geometries
         :center-on-geometry (get-in map-data [:center-on-geometry])

         ;; Use this to set a buffer for extent fitting on map view. If not defined, default value is used.
         :extent-buffer (get-in map-data [:extent-buffer])

         :rotation (if (get-in map-data [:rotate?])
                     (get-in map-data [:rotation])
                     0)

         ;; Show "foto" or "kaart" background layer from Maa-amet
         :layers (vec
                  (map-indexed (fn [i layer]
                                 {:type :maa-amet :layer layer :default true
                                  :z-index i})
                               background-layer))
         #_(vec
             (for [layer ["BAASKAART" "MAANTEED" "pohi_vr2"]]
               {:type :wms :url "http://kaart.maaamet.ee/wms/alus?"
                :layer layer
                :style ""
                :default true}))}]])))
