(ns teet.map.map-styles)


(defn map-controls
  []
  {:position :absolute
   :top "25px"
   :right "80px"
   :z-index 9999})


(defn map-controls-heading
  []
  {:background-color "black"
   :color "white"
   :padding "1rem"
   :margin 0})

(defn layer-container
  []
  {:background-color "grey"
   :color "white"
   :padding "1rem"})

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
