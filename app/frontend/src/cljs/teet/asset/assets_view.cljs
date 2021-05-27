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
            [teet.ui.material-ui :refer [CircularProgress]]))

(defn filter-component [{filters :filters-atom :as opts} attribute label component]
  [:div {:style {:margin-top "0.5rem"}}
   [typography/BoldGrayText label]
   (update component 1 merge (merge opts
                                    {:value (get @filters attribute)
                                     :on-change #(swap! filters assoc attribute %)}))])


(defn- asset-filters [e! atl filters collapsed?]
  (let [opts {:e! e!
              :filters-atom filters
              :atl atl}]
    [:div {:style {:padding "1rem"}}
     (if @collapsed?
       [:a {:on-click #(reset! collapsed? false)
            :style {:display :inline-block
                    :transform "rotate(-90deg) translateY(-30px)"
                    :cursor :pointer}}
        (tr [:search :quick-search])]

       [:<>
        [icons/navigation-arrow-left {:on-click #(reset! collapsed? true)
                                      :style {:float :right}}]
        [typography/BoldGrayText (tr [:search :quick-search])]

        [filter-component opts :fclass (tr [:asset :type-library :fclass])
         [asset-ui/select-fgroup-and-fclass-multiple {}]]

        [filter-component opts :common/status [asset-ui/label-for :common/status]
         [asset-ui/select-listitem-multiple {:attribute :common/status}]]])]))

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

(defn- assets-results [_ _ _ _]
  (let [map-key (r/atom 1)
        next-map-key! #(swap! map-key inc)]
    (r/create-class
     {:component-did-update
      (fn [this
           [_ _ _ old-query _]]
        (let [[_ _ _ new-query _] (r/argv this)]
          (when (not= old-query new-query)
            (next-map-key!))))

      :reagent-render
      (fn [e! atl assets-query {:keys [assets]}]
        [vertical-split-pane {:defaultSize 400 :primary "second"
                              :on-drag-finished next-map-key!}
         [:div
          [typography/Heading1 (tr [:asset :manager :link])]
          [table/listing-table
           {:default-show-count 100
            :columns asset-model/assets-listing-columns
            :get-column asset-model/assets-listing-get-column
            :column-label-fn #(or (some->> % (asset-type-library/item-by-ident atl) asset-ui/label)
                                  (tr [:fields %]))
            :format-column format-assets-column
            :data assets
            :key :asset/oid}]]
         ^{:key (str "map" @map-key)}
         [map-view/map-view e!
          {:full-height? true
           :layers
           {:asset-results
            (map-layers/query-layer e! :assets/geojson (:args assets-query)
                                    {:style-fn map-features/asset-line-and-icon
                                     :max-resolution 100})}}]])})))

(defn assets-page [e! app]
  (r/with-let [filters (r/atom {})
               filters-collapsed? (r/atom false)]
    (if-not (:asset-type-library app)
      [CircularProgress]
      [context/provide :rotl (asset-type-library/rotl-map (:asset-type-library app))
       [vertical-split-pane {:minSize 50
                             :defaultSize (if @filters-collapsed? 30 330)
                             :allowResize false}
        [asset-filters e! (:asset-type-library app) filters filters-collapsed?]
        [:div

         (when-let [q (assets-controller/assets-query @filters)]
           [query/query
            (merge q {:e! e!
                      :simple-view [assets-results e! (:asset-type-library app) q]})])]]])))
