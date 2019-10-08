(ns teet.road-visualization.road-visualization-view
  (:require [reagent.core :as r]
            [teet.map.map-layers :as map-layers]
            [teet.road-visualization.road-visualization-controller :as road-visualization-controller]
            [teet.map.map-view :as map-view]
            [teet.map.map-features :as map-features]
            [teet.ui.material-ui :refer [TextField Button]]
            [teet.map.openlayers :as openlayers]
            [teet.ui.util :as util]))

(defn road-visualization [e! {:keys [query road]}]
  (e! (road-visualization-controller/map->FetchRoadGeometry query))
  (let [point (r/atom [nil nil])
        set-point! #(do
                      (reset! point %)
                      (openlayers/center-map-on-point! %))]
    (fn [e! {:keys [query road]}]
      (let [[x y] @point]
        [:<>
         (when road
           [map-view/map-view e! {:layers {:road-geometry (map-layers/geojson-data-layer "road_geometry"
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
                                              {}))}}
            {}])
         [:<>
          [TextField {:value (or x "")
                      :on-change #(swap! point assoc 0 (-> % .-target .-value))
                      :label "X"}]
          [TextField {:value (or y "")
                      :on-change #(swap! point assoc 1 (-> % .-target .-value))
                      :label "Y"}]
          [Button {:on-click #(openlayers/center-map-on-point! (mapv js/parseFloat @point))} "center"]
          [:div (pr-str @point)]
          ]
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
                   [:td x] [:td y]]))]]])
         [:div "ROAD" (pr-str )]])))
  )
