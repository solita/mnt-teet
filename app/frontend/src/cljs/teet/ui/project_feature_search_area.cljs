(ns teet.ui.project-feature-search-area
  (:require [teet.project.project-controller :as project-controller]
            [teet.project.project-style :as project-style]
            [teet.ui.typography :as typography]
            [teet.ui.material-ui :refer [Paper]]
            [herb.core :as herb :refer [<class]]
            [teet.ui.text-field :refer [TextField]]))

(defn road-geometry-range-input
  [e! {road-buffer-meters :road-buffer-meters} entity-type]
  [:div {:class (<class project-style/road-geometry-range-body)}
   [TextField {:label "Inclusion distance"
               :type :number
               :placeholder "Give value to show related areas"
               :value road-buffer-meters
               :on-change #(e! (project-controller/->ChangeRoadObjectAoe (-> % .-target .-value) entity-type))}]])

(defn feature-search-area
  [e! app entity-type]
  [Paper {:class (<class project-style/road-geometry-range-selector)}
   [:div {:class (<class project-style/project-view-header)}
    [typography/Heading3 "Road geometry inclusion"]]
   [road-geometry-range-input e! app entity-type]])
