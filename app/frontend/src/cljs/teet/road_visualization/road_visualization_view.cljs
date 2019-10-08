(ns teet.road-visualization.road-visualization-view
  (:require [reagent.core :as r]
            [teet.map.map-layers :as map-layers]
            [teet.road-visualization.road-visualization-controller :as road-visualization-controller]
            [teet.map.map-view :as map-view]
            [teet.map.map-features :as map-features]
            [teet.ui.material-ui :refer [TextField Button]]
            [teet.map.openlayers :as openlayers]
            [teet.ui.util :as util]
            [teet.ui.itemlist :as itemlist]))

(defn- road-address-overlay [road-address]
  (let [{:keys [road meters carriageway]}
        (-> road-address (js->clj :keywordize-keys true))]
    [itemlist/ItemList {}
     [itemlist/Item {:label "Road"} road]
     [itemlist/Item {:label "Carriageway"} carriageway]
     [itemlist/Item {:label "km"} (.toFixed (/ meters 1000) 3)]]))

(defn road-visualization [e! {:keys [query]}]
  (e! (road-visualization-controller/map->FetchRoadGeometry query))
  (let [point (r/atom [nil nil])
        set-point! #(do
                      (reset! point %)
                      (openlayers/center-map-on-point! %))]
    (fn [e! {:keys [query road road-address]}]
      (let [[x y] @point]
        [:<>
         (when road
           [map-view/map-view e!
            {:on-click (e! road-visualization-controller/map->FetchRoadAddressForCoordinate)
             :layers {:road-geometry (map-layers/geojson-data-layer "road_geometry"
                                                                    road
                                                                    map-features/project-line-style
                                                                    {:fit-on-load? true})
                      :center-point
                      (when (and x y)
                        (map-layers/geojson-data-layer
                         "center_point"
                         #js {:type "FeatureCollection"
                              :features #js [#js {:type "Feature"
                                                  :geometry #js {:type "Point"
                                                                 :coordinates #js [(js/parseFloat x)
                                                                                   (js/parseFloat y)]}}]}

                         map-features/crosshair-pin-style
                         {}))

                      :road-address
                      (when road-address
                        (map-layers/geojson-data-layer
                         "road_address"
                         #js {:type "FeatureCollection"
                              :features #js [(aget road-address "road_part_geometry")]}
                         (partial map-features/road-line-style "magenta")
                         {}))

                      :road-address-point
                      (when road-address
                        (map-layers/geojson-data-layer
                         "road_address_point"
                         #js {:type "FeatureCollection"
                              :features #js [(aget road-address "point")]}
                         map-features/crosshair-pin-style
                         {}))}
             :overlays [(when road-address
                          {:coordinate (js->clj (aget road-address "point" "coordinates"))
                           :content [road-address-overlay road-address]})]}
            {}])
         [:<>
          [TextField {:value (or x "")
                      :on-change #(swap! point assoc 0 (-> % .-target .-value))
                      :label "X"}]
          [TextField {:value (or y "")
                      :on-change #(swap! point assoc 1 (-> % .-target .-value))
                      :label "Y"}]
          [Button {:on-click #(openlayers/center-map-on-point! (mapv js/parseFloat @point))}
           "center"]]
         (when road
           [:div  {:style {:height "350px" :overflow-y "scroll"}}
            [:table
             [:thead
              [:tr
               [:td "X"] [:td "Y"]]]
             [:tbody
              (util/with-keys
                (for [[x y] (aget road "features" 0 "geometry" "coordinates")]
                  [:tr {:on-mouse-enter (r/partial set-point! [x y])}
                   [:td x] [:td y]]))]]])]))))
