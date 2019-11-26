(ns teet.theme.container-theme
  (:require [teet.theme.theme-colors :as theme-colors]))


(defn container
  []
  {})

(defn container-control
  []
  {:display :flex
   :padding "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)
   :align-items :center
   :justify-content :space-between})

(defn heading
  []
  {:flex 1
   :display :flex
   :justify-content :space-between})

(defn collapse-button
  []
  {:margin-right "1rem"})
