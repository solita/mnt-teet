(ns teet.asset.assets-view
  (:require [reagent.core :as r]
            [teet.ui.query :as query]
            [teet.ui.typography :as typography]
            [teet.localization :refer [tr]]
            [teet.asset.asset-ui :as asset-ui]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.ui.table :as table]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.assets-controller :as assets-controller]
            [teet.ui.split-pane :refer [vertical-split-pane]]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.ui.context :as context]
            [teet.ui.icons :as icons]
            [teet.log :as log]
            [teet.ui.material-ui :refer [CircularProgress IconButton Slider]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.util.collection :as cu]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.buttons :as buttons]
            [teet.map.openlayers.layer :as layer]
            [herb.core :refer [<class]]
            [teet.util.coerce :refer [->double]]

            [ol.Map]
            [ol.layer.Vector]
            [ol.source.Vector]
            [ol.Feature]
            [ol.geom.Circle]
            [teet.ui.text-field :as text-field]
            [teet.asset.asset-styles :as asset-styles]))

(defn filter-component [{:keys [e! filters] :as opts} attribute label component]
  [:div {:style {:margin-top "0.5rem"}}
   [typography/BoldGrayText label]
   (update component 1 merge (merge opts
                                    {:value (get filters attribute)
                                     :on-change #(e! (assets-controller/->UpdateSearchCriteria {attribute %}))}))])

(defn radius-slider [{:keys [value on-change]}]
  [:div {:class (<class asset-styles/map-radius-overlay)}
   [Slider {:min 10 :max 2000
            :step 10
            :value value
            :on-change (fn [_evt v] (on-change v))}]
   [text-field/TextField
    {:hide-label? true
     :value (or value "")
     :on-change #(when-let [r (some-> % .-target .-value ->double)]
                   (when (<= 10 r 2000)
                     (on-change r)))
     :end-icon (text-field/unit-end-icon "m")}]])

(defmulti search-by-fields (fn [_e! _atl criteria] (:search-by criteria)))
(defmulti search-by-map-layers (fn [_e! _atl criteria] (:search-by criteria)))

(defmethod search-by-fields :default [_ _ _]
  [:span])

(defmethod search-by-map-layers :default [_ _ _] {})

(defn radius-display [e! {location :location radius :radius :as filters}]
  [:div {:class (<class asset-styles/map-radius-overlay-container)}
   [filter-component {:e! e! :filters filters}
    :radius (tr [:asset :manager :radius])
    [radius-slider {}]]])


(defn- location-layer [location radius]
  (let [g (ol.geom.Circle. (into-array location) radius)]
    (layer/ol-layer
     (ol.layer.Vector.
      #js {:projection "EPSG:3301"
           :source (ol.source.Vector.
                    #js {:features
                         #js [(doto (ol.Feature. #js {:geometry g} )
                                (.setProperties #js {:radius radius}))]})
           :style map-features/current-location-radius-style})
     (fn [^ol.Map ol3 _layer]
       (.fit (.getView ol3) g)))))

(defmethod search-by-map-layers :current-location
  [e! atl {:keys [location radius]}]
  (when (and location radius)
    {:search-by-current-location
     (location-layer location radius)}))

(defn- asset-filters [e! atl filters collapsed?]
  (let [opts {:e! e! :atl atl :filters filters}]
    [:div {:style {:padding "1rem"
                   :background-color theme-colors/white
                   :height "100%"}}
     (if @collapsed?
       [:<>
        [icons/navigation-arrow-right {:on-click #(reset! collapsed? false)
                                       :style {:position :relative
                                               :left "-15px"}}]
        [:a {:on-click #(reset! collapsed? false)
             :style {:display :inline-block
                     :transform "rotate(-90deg) translate(-30px,-30px)"
                     :cursor :pointer}}
         (tr [:search :quick-search])]]

       [:<>
        [icons/navigation-arrow-left {:on-click #(reset! collapsed? true)
                                      :style {:float :right}}]
        [typography/BoldGrayText (tr [:search :quick-search])]

        [filter-component opts :fclass (tr [:asset :type-library :fclass])
         [asset-ui/select-fgroup-and-fclass-multiple {}]]

        [filter-component opts :common/status [asset-ui/label-for :common/status]
         [asset-ui/select-listitem-multiple {:attribute :common/status}]]


        [buttons/button-primary {:on-click #(e! (assets-controller/->SearchByCurrentLocation))}
         "LOCATION"]

        (search-by-fields e! atl filters)])]))

(defn- format-assets-column [column value _row]
  (case column
    :location/road-address
    (let [{:location/keys [road-nr carriageway start-km end-km]} value]
      (str road-nr " / " carriageway
           (when (or start-km end-km)
             (str ": "
                  (when start-km
                    (str start-km "km"))
                  " - "
                  (when end-km
                    (str end-km "km"))))))

    (:asset/fclass :common/status)
    [asset-ui/label-for (:db/ident value)]

    (str value)))

(defn- table-and-map-toggle [show]
  [:div {:style {:position :fixed
                 :right "0px"
                 :padding "10px"
                 :top theme-spacing/appbar-height
                 :z-index 9999}}
   [IconButton {:on-click #(swap! show cu/toggle :table)
                :style (merge
                        {:border-radius "25px 0px 0px 25px"
                         :background-color theme-colors/blue-lighter}
                        (when (@show :table)
                          {:color theme-colors/white
                           :background-color theme-colors/blue}))}
    [icons/communication-list-alt]]
   [IconButton {:on-click #(swap! show cu/toggle :map)
                :style (merge
                        {:border-radius "0px 25px 25px 0px"
                         :background-color theme-colors/blue-lighter}
                        (when (@show :map)
                          {:color theme-colors/white
                           :background-color theme-colors/blue}))}
    [icons/maps-map]]])

(defn- assets-results [_ _ _ _ _] ;; FIXME: bad name, it is shown always
  (let [show (r/atom #{:map})
        map-key (r/atom 1)
        next-map-key! #(swap! map-key inc)]
    (r/create-class
     {:component-did-update
      (fn [this
           [_ _ _ _ old-query _]]
        (let [[_ _ _ _ new-query _] (r/argv this)]
          (when (not= old-query new-query)
            (next-map-key!))))

      :reagent-render
      (fn [e! atl criteria assets-query
           {:keys [assets geojson more-results? result-count-limit
                   highlight-oid]}]
        (let [table-pane
              [:div {:style {:background-color theme-colors/white
                             :padding "0.5rem"}}
               [typography/Heading1 (tr [:asset :manager :result-count]
                                        {:count (if more-results?
                                                  (str result-count-limit "+")
                                                  (count assets))})]
               [table/listing-table
                {:default-show-count 100
                 :columns asset-model/assets-listing-columns
                 :get-column asset-model/assets-listing-get-column
                 :column-label-fn #(or (some->> % (asset-type-library/item-by-ident atl) asset-ui/label)
                                       (tr [:fields %]))
                 :on-row-hover (e! assets-controller/->HighlightResult)
                 :format-column format-assets-column
                 :data assets
                 :key :asset/oid}]]

              map-pane
              ^{:key (str "map" @map-key)}
              [:<>
               [map-view/map-view e!
                {:full-height? true
                 :layers
                 (merge
                  (search-by-map-layers e! atl criteria)
                  (when geojson
                    {:asset-results
                     (map-layers/geojson-data-layer
                      "asset-results"
                      (js/JSON.parse geojson)
                      (partial map-features/asset-line-and-icon highlight-oid)
                      {;:fit-on-load? true
                       })}))}]
               (when (= :current-location (:search-by criteria))
                 [radius-display e! criteria])]]
          [:<>
           [table-and-map-toggle show]
           (condp = @show
             #{:map}
             ^{:key "map-only"}
             map-pane

             #{:table}
             table-pane

             [vertical-split-pane
              {:defaultSize 400 :primary "second"
               :on-drag-finished next-map-key!}
              table-pane map-pane])]))})))

(defn assets-page [e! {atl :asset-type-library :as _app}
                   {:keys [criteria query results]}]
  (r/with-let [filters-collapsed? (r/atom false)]
    (if-not atl
      [CircularProgress]
      [context/provide :rotl (asset-type-library/rotl-map atl)
       [vertical-split-pane {:minSize 50
                             :defaultSize (if @filters-collapsed? 30 330)
                             :allowResize false}
        [asset-filters e! atl criteria filters-collapsed?]
        [assets-results e! atl criteria query results]]])))
