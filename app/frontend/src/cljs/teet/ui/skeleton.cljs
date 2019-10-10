(ns teet.ui.skeleton
  (:require [reagent.core :as r]
            [herb.core :refer [<class defkeyframes]]))

(def background )

(defkeyframes skeleton-animation
              ["0%" {:opacity 1}]
              ["50%" {:opacity "0.4"}]
              ["100%" {:opacity 1}])

(defn skeleton-style
  [opts]
  (merge {:animation [[skeleton-animation "1.5s" :infinite :ease-in-out]]
          :background "#c3c3c3"
          :height "1.2em"
          :border-radius "5px"}
         opts))

(defn skeleton
  [{:keys [width] :as opts}]
  [:div {:class (<class skeleton-style opts)}])
