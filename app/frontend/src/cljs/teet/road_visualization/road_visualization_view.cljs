(ns teet.road-visualization.road-visualization-view
  (:require [reagent.core :as r]
            [teet.map.map-layers :as map-layers]
            [teet.road-visualization.road-visualization-controller :as road-visualization-controller]
            [teet.map.map-view :as map-view]
            [teet.map.map-features :as map-features]
            [teet.ui.material-ui :refer [Button]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.map.openlayers :as openlayers]
            [teet.ui.util :as util]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.layout :as layout]))

(defn- road-address-overlay [road-address]
  (let [{:keys [road name meters carriageway start-m end-m]}
        (-> road-address (js->clj :keywordize-keys true))]
    [itemlist/ItemList {}
     [itemlist/Item {:label "Road"} (str road " (" name ")")]
     [itemlist/Item {:label "Carriageway"} carriageway]
     [itemlist/Item {:label "km"} (.toFixed (/ meters 1000) 3)]
     [itemlist/Item {:label "Start meters"} start-m]
     [itemlist/Item {:label "End meters"} end-m]]))

(defn road-visualization [e! {:keys [query]}]
  (let [point (r/atom [nil nil])
        set-point! #(do
                      (reset! point %)
                      (openlayers/center-map-on-point! %))]
    (r/create-class
      {:component-did-mount
       (fn []
         (e! (road-visualization-controller/map->FetchRoadGeometry query)))
       :reagent-render
       (fn [e! {:keys [query road-data]}]
         (let [[x y] @point
               road-line-string (:road-line-string road-data)]
           [:<>
            [map-view/map-view e!
             {:on-click (e! road-visualization-controller/map->FetchRoadAddressForCoordinate)
              :layers {:road-geometry
                       (when road-line-string
                         (map-layers/geojson-data-layer
                          "road_geometry"
                          #js {:type "FeatureCollection"
                               :features #js [#js {:type "Feature"
                                                   :properties #js {}
                                                   :geometry #js {:type "LineString"
                                                                  :coordinates (into-array
                                                                                (map into-array road-line-string))}}]}
                          map-features/project-line-style
                          {:fit-on-load? true}))

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
                          {}))}
              :overlays [#_(when road-address
                           {:coordinate (js->clj (aget road-address "point" "coordinates"))
                            :content [road-address-overlay road-address]})]}
             {}]
            [layout/section
             [:div {:style {:display :flex}}
              [:div {:style {:flex 2}}
               [:<>
                [TextField {:value (or x "")
                            :on-change #(swap! point assoc 0 (-> % .-target .-value))
                            :label "X"}]
                [TextField {:value (or y "")
                            :on-change #(swap! point assoc 1 (-> % .-target .-value))
                            :label "Y"}]
                [Button {:on-click #(openlayers/center-map-on-point! (mapv js/parseFloat @point))}
                 "center"]]
               #_(if (and road (aget road "features" 0 "geometry"))
                 [:div {:style {:height "350px" :overflow-y "scroll"}}
                  [:table
                   [:thead
                    [:tr
                     [:td "X"] [:td "Y"]]]
                   [:tbody
                    (util/with-keys
                      (for [[x y] (aget road "features" 0 "geometry" "coordinates")]
                        [:tr {:tab-index 1
                              :on-focus (r/partial set-point! [x y])
                              :on-mouse-over (r/partial set-point! [x y])}
                         [:td x] [:td y]]))]]]
                 [typography/Heading3 "No road information found"])]
              [:div {:style {:flex 1
                             :padding "1rem"}}
               [typography/Heading3 {:style {:margin-bottom "1rem"}}
                "Select road information"]
               [:p {:style {:color "red"}} "(This page is just a proof o concept for visualizing road information on a map)"]
               [:form {:style {:display :flex
                               :flex-direction :column
                               :justify-content :space-between}
                       :on-submit (fn [e]
                                    (.preventDefault e)
                                    (e! (road-visualization-controller/map->FetchRoadGeometry query)))}
                [TextField {:label "Carriageway"
                            :variant :outlined
                            :name "carriageway"
                            :on-change #(e! (road-visualization-controller/->QueryFieldChange :carriageway %))
                            :value (or (:carriageway query) "")}]
                [TextField {:label "Road number"
                            :style {:margin-bottom "1rem"}
                            :variant :outlined
                            :name "road"
                            :on-change #(e! (road-visualization-controller/->QueryFieldChange :road %))
                            :value (or (:road query) "")}]
                [TextField {:label "Start meters"
                            :style {:margin-bottom "1rem"}
                            :variant :outlined
                            :on-change #(e! (road-visualization-controller/->QueryFieldChange :start-m %))
                            :name "start-m"
                            :value (or (:start-m query) "")}]
                [TextField {:label "End meters"
                            :style {:margin-bottom "1rem"}
                            :variant :outlined
                            :on-change #(e! (road-visualization-controller/->QueryFieldChange :end-m %))
                            :name "end-m"
                            :value (or (:end-m query) "")}]
                [Button
                 {:type :submit
                  :variant :outlined}
                 "Get road information"]]]]]]))})))
