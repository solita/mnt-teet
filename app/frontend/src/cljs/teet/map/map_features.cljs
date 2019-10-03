(ns teet.map.map-features
  "Defines rendering styles for map features "
  (:require [ol.style.Style]
            [ol.style.Icon]
            [ol.style.Stroke]
            [ol.style.Fill]
            [ol.style.Icon]
            [ol.render.Feature]))

(defn project-line-style
  "Show project geometry as the road line."
  [^ol.render.Feature feature res]
  (let [line-width (+ 3 (min 5 (int (/ 200 res))))]
    ;; Show project road geometry line
    (ol.style.Style.
     #js {:stroke (ol.style.Stroke. #js {:color "blue"
                                         :width line-width
                                         :lineCap "butt"})
          :zIndex 2})))

(defn project-related-restriction-style
  "Show project related restriction as a filled area."
  [^ol.render.Feature feature res]
  (ol.style.Style.
   #js {:fill (ol.style.Fill. #js {:color "#f26060"})
        :zIndex 3}))

(defn project-pin-style
  "Show project centroid as a pin icon."
  [^ol.render.Feature feature res]
  (ol.style.Style.
   #js {:image (ol.style.Icon.
                #js {:src "/img/pin.png"
                     :anchor #js [0.5 1.0]})}))
