(ns teet.common.common-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn gray-light-border
  []
  {:display :flex
   :align-items :center
   :padding-bottom "0.75rem"
   :margin-bottom "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-light)})

(defn top-info-spacing
  []
  {:padding "1.5rem 1.875rem"})

(defn spinner-style
  []
  {:height "100%"
   :width "100%"
   :align-items :center
   :display :flex
   :justify-content :center})

(defn input-error-text-style
  []
  {:font-size "1rem"
   :color theme-colors/error
   :position :absolute})

(defn grid-left-item
  []
  {:display        :flex
   :flex-direction :column})

(defn tab-icon []
  ^{:pseudo {:hover {:fill theme-colors/blue-dark}}}
  {:fill theme-colors/blue-light})
