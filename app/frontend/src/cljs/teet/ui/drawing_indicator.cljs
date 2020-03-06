(ns teet.ui.drawing-indicator
  (:require [teet.ui.material-ui :refer [ButtonGroup Button]]
            [herb.core :refer [<class]]
            [teet.ui.icons :as icons]
            [reagent.core :as r]))


(defn button-class
  []
  {:border-radius 0})

(defn drawing-indicator
  [cancel-action]
  [:div {:style {:position :absolute
                 :top "25px"
                 :right "75px"}}

   [Button {:color :primary
            :class (<class button-class)
            :disable-ripple true
            :on-click cancel-action
            :variant :contained
            :start-icon (r/as-element [icons/image-edit])}
    [:span  "drawing"
     [:span "Cancel"]]]])
