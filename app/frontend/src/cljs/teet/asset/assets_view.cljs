(ns teet.asset.assets-view
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid Paper]]
            [teet.ui.query :as query]
            [teet.common.common-styles :as common-styles]
            [teet.ui.typography :as typography]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            [teet.asset.asset-ui :as asset-ui]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.ui.table :as table]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.assets-controller :as assets-controller]
            [teet.ui.split-pane :refer [vertical-split-pane]]))

(defn- asset-filters [e! atl filters]
  [:div {:style {:min-width "300px"}}
   [:div {:style {:padding "1rem"}}
    [asset-ui/select-fgroup-and-fclass-multiple
     {:e! e!
      :value (@filters :fclass)
      :on-change #(swap! filters assoc :fclass %)
      :atl atl}]]])

(defn- assets-results [e! atl {:keys [assets geojson]}]
  (let [fitted (atom false)]
    (r/create-class
     {:component-did-update
      (fn [_
           [_ _ _ {old-geojson :geojson}]
           [_ _ _ {new-geojson :geojson}]]
        (when (not= old-geojson new-geojson)
          (reset! fitted false)))
      :reagent-render
      (fn [e! atl {:keys [assets geojson]}]
        [vertical-split-pane {:defaultSize 400 :primary "second"}
         [table/listing-table
          {:columns asset-model/assets-listing-columns
           :data assets
           :key :asset/oid}]
         [map-view/map-view e!
          {:full-height? true
           :layers (when geojson
                     {:assets-search-geojson
                      (map-layers/geojson-data-layer
                       "assets-search-geojson"
                       (js/JSON.parse geojson)
                       map-features/asset-line-and-icon
                       {:fit-on-load? true
                        :fitted-atom fitted})})}]])})))

(defn assets-page [e! app]
  (r/with-let [filters (r/atom {})]
    [vertical-split-pane {:minSize 50
                          :defaultSize 330}
     [asset-filters e! (:asset-type-library app) filters]
     [:div
      [typography/Heading1 (tr [:asset :manager :link])]
      (when-let [q (assets-controller/assets-query @filters)]
        [query/query
         (merge q {:e! e!
                   :simple-view [assets-results e! (:asset-type-library app)]})])]]))
