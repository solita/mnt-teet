(ns teet.theme.theme-panels
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.theme.theme-spacing :as theme-spacing]))

(defn side-panel []
  {:background-color theme-colors/gray-lighter
   :min-height theme-spacing/content-height
   :padding "1rem"})

(defn popup-panel
  "Small popup info panel. Should be anchored to some element."
  []
  ^{:pseudo {:after {:content "''"
                     :border-width "10px"
                     :border-color (str theme-colors/popup-border " transparent transparent transparent")
                     :border-style "solid"
                     :display "block"
                     :position "absolute"
                     :left "50%"
                     :transform "translate(-50%)"
                     :bottom "-20px"}}}
  {:background-color theme-colors/popup-background
   :position "relative"
   :margin-bottom "15px"
   :max-width "20vw"
   :border-radius 5
   :padding "0.25rem"
   :box-shadow (str "0px 2px 4px " theme-colors/popup-border)})
