(ns teet.ui.skeleton
  (:require [reagent.core :as r]
            [herb.core :refer [<class defkeyframes]]))

(defkeyframes skeleton-animation
  ["0%" {:opacity 1}]
  ["50%" {:opacity "0.4"}]
  ["100%" {:opacity 1}])

(defn skeleton-style
  [opts]
  (merge {:background "#e6e6e6"
          :height "1.2em"
          :border-radius "5px"
          :margin 0}
    opts))

(defn restriction-skeleton-style
  [& opts]
  (merge {:border-color "#e6e6e6"
          :border-width "1px 0"
          :border-style "solid"
          :padding "1.5rem 0"}
    opts))

(defn skeleton-parent-style
  [opts]
  (merge
    {:animation [[skeleton-animation "1.5s" :infinite :ease-in-out]]
     :border-radius "5px"
     :border-color "#e6e6e6"}
    opts))

(defn skeleton
  [{:keys [parent-style style]}]
  [:div {:class (<class skeleton-parent-style parent-style)}
   [:p {:class (<class skeleton-style style)}]])

(defn restrictions-skeleton
  []
  [:div {:class (<class restriction-skeleton-style)}
   [skeleton]])
