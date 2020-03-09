(ns teet.theme.theme-panels
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.theme.theme-spacing :as theme-spacing]))

(defn side-panel []
  {:background-color theme-colors/gray-lighter
   :min-height theme-spacing/content-height
   :padding "1rem"})

(defn popup-panel
  "Small popup info panel. Should be anchored to some element."
  [arrow-placement]
  (with-meta
    {:background-color theme-colors/popup-background
     :position "relative"
     :top (case arrow-placement
            :top "10px"
            :bottom "0px")
     :margin-bottom "12px"
     :max-width "30vw"
     :border-radius 5
     :padding "0.5rem"
     :box-shadow (str "0px 2px 4px " theme-colors/popup-border)}
    {:pseudo
     {:after (merge {:content "''"
                     :border-width "10px"
                     :border-color (str theme-colors/popup-border " transparent transparent transparent")
                     :border-style "solid"
                     :display "block"
                     :position "absolute"
                     :left "50%"}
                    (case arrow-placement
                      :bottom {:bottom "-20px"
                               :transform "translate(-50%)"}
                      :top {:top "-20px"
                            :transform "translate(-50%) rotateX(180deg)"}))}}))
