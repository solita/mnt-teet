(ns teet.project.search-area-view
  (:require [teet.project.search-area-controller :as search-area-controller]
            [teet.project.project-style :as project-style]
            [teet.ui.typography :as typography]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr]]
            [teet.project.search-area-style :as search-area-style]
            [teet.ui.material-ui :refer [Paper Tab Tabs]]
            [herb.core :refer [<class]]
            [teet.ui.text-field :refer [TextField]]
            [reagent.core :as r]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.common :as common]
            [teet.ui.buttons :as buttons]))


(defn road-geometry-range-input
  [e! {road-buffer-meters :road-buffer-meters} entity-type]
  [:div {:class (<class search-area-style/road-geometry-range-body)}
   [TextField {:label (tr [:search-area :inclusion-distance])
               :type :number
               :placeholder (tr [:search-area :inclusion-distance-placeholder])
               :value road-buffer-meters
               :on-change #(e! (search-area-controller/->ChangeRoadObjectAoe (-> % .-target .-value) entity-type))}]])

(defn drawn-road-areas
  [e! _ _]
  (r/create-class
    {:display-name "Project drawn interest areas"
     :component-will-unmount (e! search-area-controller/->StopCustomAreaDraw)
     :component-did-mount (e! search-area-controller/->FetchDrawnAreas)
     :reagent-render
     (fn [e! {:keys [map]} project]
       (let [drawing? (get-in map [:search-area :drawing?])
             drawn-geometries (get-in map [:search-area :drawn-areas])]
         [:div {:style {:padding "1rem"}}
          [:span (tr [:search-area :inclusion-areas])]
          (doall
            (for [{:keys [label id] :as feature} drawn-geometries]
              ^{:key id}
              [common/feature-and-action {:label label
                                          :id id}
               {:button-label (tr [:buttons :delete])
                :on-mouse-enter #(e! (search-area-controller/->MouseOverDrawnAreas "project-drawn-areas" true feature))
                :on-mouse-leave #(e! (search-area-controller/->MouseOverDrawnAreas "project-drawn-areas" false feature))
                :action #(e! (search-area-controller/->DeleteDrawnArea id (:db/id project)))}]))
          [buttons/add-button {:on-click #(e! (search-area-controller/->InitializeCustomAreaDraw))
                               :disabled? drawing?}
           (if drawing?
             [:span {:style {:color theme-colors/gray-lightest}}
              (tr [:search-area :waiting-for-area]) "..."]
             [icons/content-add-circle {:size :large}])]]))}))

(defn feature-search-area
  [e! _ _ _]
  (r/create-class
    {:display-name "Project search area component"
     :component-will-unmount (e! search-area-controller/->UnMountSearchComponent)
     :reagent-render
     (fn [e! {:keys [map] :as app} project entity-type]
       (let [tab (or (get-in map [:search-area :tab]) :buffer-area)]
         [Paper {:class (<class search-area-style/road-geometry-range-selector)}
          [:div {:class (<class project-style/project-view-header)}
           [typography/Heading3 (tr [:search-area :component-title])]]
          [Tabs {:on-change (fn [_ v]
                              (e! (search-area-controller/->ChangeTab (keyword v))))
                 :value tab}
           (Tab {:key 1
                 :value :buffer-area
                 :index 1
                 :label (tr [:search-area :filter-by-distance])})
           (Tab {:key 2
                 :value :drawn-area
                 :index 2
                 :label (tr [:search-area :filter-by-area])})]
          (case tab
            :buffer-area
            [road-geometry-range-input e! map entity-type]
            :drawn-area
            [drawn-road-areas e! app project])]))}))
