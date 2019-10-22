(ns teet.map.map-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn map-controls
  []
  {:background-color "white"
   :position :absolute
   :top "25px"
   :right "80px"
   :z-index 9999
   :box-shadow "0px 2px 8px rgba(0, 0, 0, 0.25)"})

(defn category-collapse-button
  []
  {:margin-right "1rem"})

(defn map-controls-heading
  []
  {:display :flex
   :justify-content :space-between
   :align-items :center
   :padding "1rem"
   :margin 0
   :border-bottom (str "1px solid " theme-colors/gray-light)})

(defn category-container
  []
  {})

(defn category-control
  []
  {:display :flex
   :padding "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)
   :align-items :center
   :justify-content :space-between})

(defn category-selections
  []
  {:padding "0.5rem 1rem"
   :background-color theme-colors/gray-lightest
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})

(defn map-control-buttons
  []
  {:position :absolute
   :top "25px"
   :right "25px"
   :z-index 9999})

(defn map-control-button
  []
  {:opacity "0.8"
   :transition "all 0.2s ease-in-out"})

(defn map-overlay
  []
  {:position "relative"
   :left "10px"
   :padding "0.5rem"
   :background-color "wheat"})

(defn layer-checkbox
  []
  {:margin-right "0.5rem"})

(defn checkbox-label
  [selected?]
  {:font-weight (if selected?
                  "bold !important"
                  "normal")})
