(ns teet.project.search-area-style
  (:require [teet.map.map-styles :as map-styles]))

(defn road-geometry-range-body
  []
  {:padding "1rem"})

(defn road-geometry-range-selector
  []
  (map-styles/map-controls {:position :bottom}))
