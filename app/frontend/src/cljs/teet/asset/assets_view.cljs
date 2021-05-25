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
            [teet.common.common-controller :as common-controller]))

(defn- asset-filters [e! atl filters]
  [:div {:style {:min-width "300px"}}
   [:div {:style {:padding "1rem"}}
    [asset-ui/select-fgroup-and-fclass-multiple
     {:e! e!
      :value (@filters :fclass)
      :on-change #(swap! filters assoc :fclass %)
      :atl atl}]]])

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

    :asset/fclass
    [asset-ui/label-for (:db/ident value)]

    (str value)))

(defn- assets-results [_ _ _ _]
  (let [fitted (atom false)
        map-key (r/atom 1)
        next-map-key! #(swap! map-key inc)]
    (r/create-class
     {:component-did-update
      (fn [_
           [_ _ _ {old-geojson :geojson}]
           [_ _ _ {new-geojson :geojson}]]
        (when (not= old-geojson new-geojson)
          (reset! fitted false)))
      :reagent-render
      (fn [e! _atl assets-query {:keys [assets geojson]}]
        [vertical-split-pane {:defaultSize 400 :primary "second"
                              :on-drag-finished next-map-key!}
         [:div
          [typography/Heading1 (tr [:asset :manager :link])]
          [table/listing-table
           {:default-show-count 100
            :columns asset-model/assets-listing-columns
            :get-column asset-model/assets-listing-get-column
            :format-column format-assets-column
            :data assets
            :key :asset/oid}]]
         ^{:key (str "map" @map-key)}
         [map-view/map-view e!
          {:full-height? true
           :layers
           (when geojson
             {:assets-mvt (map-layers/mvt-layer
                           {:url-fn #(common-controller/query-url
                                      :assets/mvt
                                      (merge (:args assets-query)
                                             %))}
                           "foo" {}
                           map-features/asset-line-and-icon
                           {})
              :assets-search-geojson
              (map-layers/geojson-data-layer
               "assets-search-geojson"
               (js/JSON.parse geojson)
               map-features/asset-line-and-icon
               {:fit-on-load? true
                :fitted-atom fitted})})}]])})))

(defn assets-page [e! app]
  (r/with-let [filters (r/atom {})]
    [context/provide :rotl (asset-type-library/rotl-map (:asset-type-library app))
     [vertical-split-pane {:minSize 50
                           :defaultSize 330
                           :allowResize false}
      [asset-filters e! (:asset-type-library app) filters]
      [:div

       (when-let [q (assets-controller/assets-query @filters)]
         [query/query
          (merge q {:e! e!
                    :simple-view [assets-results e! (:asset-type-library app) q]})])]]]))
