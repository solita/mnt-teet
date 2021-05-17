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
            [teet.asset.assets-controller :as assets-controller]))

(defn- asset-filters [e! atl filters]
  [:div {:style {:min-width "300px"}}
   [:div {:style {:padding "1rem"}}
    [asset-ui/select-fgroup-and-fclass-multiple
     {:e! e!
      :value (@filters :fclass)
      :on-change #(swap! filters assoc :fclass %)
      :atl atl}]]])

(defn- assets-results [e! atl {:keys [assets geojson]}]
  [:div
   [table/listing-table
    {:columns asset-model/assets-listing-columns
     :data assets
     :key :asset/oid}]
   [map-view/map-view e!
    {:layers (when geojson
               {:assets-search-geojson
                (map-layers/geojson-data-layer
                 "assets-search-geojson"
                 (js/JSON.parse geojson)
                 map-features/project-line-style
                 {:fit-on-load? true})})}]])

(defn assets-page [e! app]
  (r/with-let [filters (r/atom {})]
    [:div {:style {:padding "1.875rem 1.5rem"
                   :display :flex
                   :height "calc(100vh - 220px)"
                   :flex-direction :column
                   :flex 1}}
     [Paper {:style {:display :flex :flex 1}}
      [:div {:style {:width "30vw"}}
       [asset-filters e! (:asset-type-library app) filters]]
      [:div {:style {:flex 1 :overflow-y :scroll
                     :max-height "calc(100vh - 170px)"
                     :padding "1rem"}}
       [:div {:class (<class common-styles/flex-row-space-between)}
        [typography/Heading1 (tr [:asset :manager :link])]]
       (when-let [q (assets-controller/assets-query @filters)]
         [query/query
          (merge q {:e! e!
                    :simple-view [assets-results e! (:asset-type-library app)]})])]]]))
