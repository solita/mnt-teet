(ns teet.ui.drawing-indicator
  (:require [teet.ui.material-ui :refer [Button]]
            [herb.core :refer [<class]]
            [teet.ui.icons :as icons]
            [reagent.core :as r]
            [teet.localization :refer [tr]]
            [teet.theme.theme-colors :as theme-colors]))

(defn button-style
  []
  {:border-radius "0"
   :height "40px"
   :padding "4px 0.5rem"})

(defn secondary-button-style
  []
  (merge
    (button-style)
    {:background-color theme-colors/blue-light}))

(defn drawing-indicator-container-style
  []
  {:border-radius "2px"
   :overflow "hidden"
   :position :absolute
   :display :flex
   :top "25px"
   :right "75px"
   :box-shadow "0px 3px 5px -1px rgba(0,0,0,0.2),0px 6px 10px 0px rgba(0,0,0,0.14),0px 1px 18px 0px rgba(0,0,0,0.12)"})

(defn drawing-indicator-style
  []
  {:background-color theme-colors/blue-light
   :display :flex
   :justify-content :center
   :align-items :center
   :color theme-colors/blue-lighter
   :padding "4px 0.5rem"})

(defn drawing-indicator
  [{:keys [cancel-action save-action save-disabled?]}]
  [:div {:class (<class drawing-indicator-container-style)}
   [:p {:class (<class drawing-indicator-style)}
    [icons/image-edit {:style {:margin-right "0.5rem"}}]
    "Drawing..."]
   [Button {:color :primary
            :class (<class secondary-button-style)
            :disable-ripple true
            :on-click cancel-action
            :variant :contained}
    [:span (tr [:buttons :cancel])]]
   [Button {:color :primary
            :class (<class button-style)
            :disable-ripple true
            :disabled save-disabled?
            :on-click save-action
            :variant :contained}
    [:span (tr [:buttons :save])]]])
