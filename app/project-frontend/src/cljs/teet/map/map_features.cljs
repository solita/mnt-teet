(ns teet.map.map-features
  "Defines rendering styles for map features "
  (:require [ol.style.Style]
            [ol.style.Icon]
            [ol.style.Stroke]
            [ol.style.Fill]))

(defn project-style [feature res]
  (ol.style.Style.
   #js {:stroke (ol.style.Stroke. #js {:color "blue"
                                       :width 8
                                       :lineCap "butt"})
        :zIndex 2}))
