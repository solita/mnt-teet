(ns teet.map.map-styles)


(defn map-controls
  []
  {:position         :absolute
   :top              "25px"
   :right            "100px"
   :z-index          9999})


(defn map-controls-heading
  []
  {:background-color "black"
   :color            "white"
   :padding          "1rem"
   :margin           0})

(defn layer-container
  []
  {:background-color "grey"
   :color            "white"
   :padding          "1rem"})

(defn map-controls-button
  []
  {:position :absolute
   :top      "25px"
   :right    "25px"
   :z-index  9999})
