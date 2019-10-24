(ns teet.theme.theme-panels
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.theme.theme-spacing :as theme-spacing]))

(defn side-panel []
  {:background-color theme-colors/gray-lighter
   :min-height theme-spacing/content-height
   :padding "1rem"})
