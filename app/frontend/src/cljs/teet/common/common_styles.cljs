(ns teet.common.common-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn gray-light-border
  []
  {:display :flex
   :align-items :center
   :padding-bottom "0.75rem"
   :margin-bottom "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-light)})

(defn project-info-spacing
  []
  {:padding "1rem 1.5rem 0 1.5rem"})

(defn spinner-style
  []
  {:height "100%"
   :width "100%"
   :align-items :center
   :display :flex
   :justify-content :center})
