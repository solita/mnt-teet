(ns teet.project.search-area-view
  (:require [teet.project.search-area-controller :as search-area-controller]
            [teet.project.project-style :as project-style]
            [teet.ui.typography :as typography]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr]]
            [teet.project.search-area-style :as search-area-style]
            [teet.ui.material-ui :refer [Paper Tab Tabs ButtonBase]]
            [herb.core :refer [<class]]
            [teet.ui.text-field :refer [TextField]]
            [reagent.core :as r]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]))

(defn feature-and-action-style
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-dark}}}
  {:background-color theme-colors/gray
   :color theme-colors/white
   :transition "background-color 200ms ease-in-out"
   :padding "0.5rem"
   :padding-left "1rem"
   :margin-bottom "0.5rem"
   :display :flex
   :justify-content :space-between
   :align-items :center})

(defn feature-and-action
  [{:keys [label]}
   {:keys [button-label action on-mouse-enter on-mouse-leave]}]
  [:div {:on-mouse-enter on-mouse-enter
         :on-mouse-leave on-mouse-leave
         :class (<class feature-and-action-style)}
   [:span label]
   [buttons/rect-white {:on-click action}
    button-label]])


(defn- add-area-button
  [{:keys [on-click disabled?]} label]
  [ButtonBase {:on-click on-click
               :disable-ripple true
               :disabled disabled?
               :class (<class search-area-style/add-area-button-style disabled?)}
   label])

(defn road-geometry-range-input
  [e! {road-buffer-meters :road-buffer-meters} entity-type]
  [:div {:class (<class search-area-style/road-geometry-range-body)}
   [TextField {:label "Inclusion distance"
               :type :number
               :placeholder "Give value to show related areas"
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
          [:span "Inclusion areas"]
          (doall
            (for [{:keys [label id] :as feature} drawn-geometries]
              ^{:key id}
              [feature-and-action {:label label
                                   :id id}
               {:button-label (tr [:buttons :delete])
                :on-mouse-enter #(e! (search-area-controller/->MouseOverDrawnAreas "project-drawn-areas" true feature))
                :on-mouse-leave #(e! (search-area-controller/->MouseOverDrawnAreas "project-drawn-areas" false feature))
                :action #(e! (search-area-controller/->DeleteDrawnArea id (:db/id project)))}]))
          [add-area-button {:on-click #(e! (search-area-controller/->InitializeCustomAreaDraw))
                            :disabled? drawing?}
           (if drawing?
             [:span {:style {:color theme-colors/gray-lightest}}
              "Waiting for area..."]
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
           [typography/Heading3 "Road geometry inclusion"]]
          [Tabs {:on-change (fn [_ v]
                              (e! (search-area-controller/->ChangeTab (keyword v))))
                 :value tab}
           (Tab {:key 1
                 :value :buffer-area
                 :index 1
                 :label "Buffer area"})
           (Tab {:key 2
                 :value :drawn-area
                 :index 2
                 :label "Drawn area"})]
          (case tab
            :buffer-area
            [road-geometry-range-input e! map entity-type]
            :drawn-area
            [drawn-road-areas e! app project])]))}))
