(ns teet.project.search-area-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn road-geometry-range-body
  []
  {:padding "1rem"})

(defn road-geometry-range-selector
  []
  {:position :absolute
   :bottom "25px"
   :right "25px"})

(defn add-area-button-style
  [disabled?]
  (with-meta
    (merge {:border "1px solid black"
            :transition "background-color 0.2s ease-in-out"
            :display :flex
            :width "100%"
            :height "42px"
            :justify-content :center
            :padding "0.5rem"}
           (when disabled?
             {:background-color teet.theme.theme-colors/gray-light}))
    (when (not disabled?)
      {:pseudo {:hover {:background-color theme-colors/gray-lightest}
                :active {:background-color theme-colors/gray-light}}})))
