(ns teet.asset.asset-styles
  "Herb style defs for asset related pages"
  (:require [teet.theme.theme-colors :as theme-colors]))


(defn map-radius-overlay
  "Style for radius slider and input box"
  []
  ^{:combinators {[:> :label :div :input]
                  {:position :relative
                   :top "-10px"
                   :margin-left "5px"}}}
  {:display :flex
   :flex-direction :row})

(defn map-radius-overlay-container
  []
  {:position :fixed
   :bottom "10px"
   :padding "0.5rem"
   :margin-left "10px"
   :z-index 9999
   :background-color theme-colors/gray-lighter
   :border-radius "5px"})
