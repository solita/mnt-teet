(ns teet.ui.drawing-indicator
  (:require [teet.ui.material-ui :refer [Button]]
            [herb.core :refer [<class]]
            [teet.ui.icons :as icons]
            [reagent.core :as r]))


(defn button-style
  []
  {:border-radius "2px"
   :height "40px"
   :box-shadow "0px 3px 5px -1px rgba(0,0,0,0.2),0px 6px 10px 0px rgba(0,0,0,0.14),0px 1px 18px 0px rgba(0,0,0,0.12)"})

(defn drawing-indicator-container-style
  []
  {:position :absolute
   :top "25px"
   :right "75px"})

(defn drawing-indicator
  [cancel-action]
  [:div {:class (<class drawing-indicator-container-style)}
   [Button {:color :primary
            :class (<class button-style)
            :disable-ripple true
            :on-click cancel-action
            :variant :contained
            :start-icon (r/as-element [icons/image-edit])}
    [:span "Cancel drawing"]]])
