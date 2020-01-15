(ns teet.map.map-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn map-controls
  []
  {:background-color "white"
   :max-width "40vw"
   :position :absolute
   :top "25px"
   :right "80px"
   :z-index 999
   :box-shadow "0px 2px 8px rgba(0, 0, 0, 0.25)"})

(defn category-collapse-button
  []
  {:margin-right "1rem"})

(defn map-controls-heading
  []
  {:display :flex
   :justify-content :space-between
   :align-items :center
   :padding "1rem"
   :margin 0
   :border-bottom (str "1px solid " theme-colors/gray-light)})

(defn category-container
  []
  {})

(defn category-control
  []
  {:display :flex
   :padding "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)
   :align-items :center
   :justify-content :space-between})

(defn category-selections
  []
  {:padding "0.5rem 1rem"
   :background-color theme-colors/gray-lightest
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})

(defn map-control-buttons
  []
  {:position :absolute
   :display  :flex
   :flex-direction :column
   :top      "20px"
   :right    "25px"
   :z-index  999})

(defn map-control-button
  []
  {:opacity "0.9"
   :margin-top "5px"
   :transition "all 0.2s ease-in-out"})

(defn map-overlay
  []
  {}
  #_{:right 0
   :position :absolute})

(def overlay-background-color "#005E87")

(defn map-overlay-container [width height arrow-direction]
  (let [half-height (when height
                      (str (/ height 2) "px"))
        half-width (when width
                     (str (/ width 2) "px"))
        positioning (cond
                      (= arrow-direction :right)
                      {:right half-height}

                      (= arrow-direction :left)
                      {:left half-height}

                      (= arrow-direction :top)
                      {:left (str "-" half-width)
                       :top "20px"}

                      :else
                      {})]
    (merge
     (when width
       {:width (str width "px")})
     (when height
       {:height (str height "px")})
     {:background-color overlay-background-color
      :position :absolute
      :display :flex
      :align-items :center
      :top (str "-" half-height)}
     positioning)))

(defn map-overlay-arrow [width height arrow-direction]
  (let [half-height (when height
                      (str (/ height 2) "px"))
        half-width (when width
                     (str (/ width 2) "px"))]
    (merge
     {:width 0
      :height 0
      :position :absolute}
     (case arrow-direction
       :right {:border-top (str half-height " solid transparent")
               :border-bottom (str half-height " solid transparent")
               :border-left (str half-height " solid " overlay-background-color)
               :right (str "-" half-height)}
       :left {:border-top (str half-height " solid transparent")
              :border-bottom (str half-height " solid transparent")
              :left (str "-" half-height)
              :border-right (str half-height " solid " overlay-background-color)}
       :top {:border-left "20px solid transparent"
             :border-right "20px solid transparent"
             :left (str "calc(" half-width " - 20px)")
             :top "-14px"
             :border-bottom (str "15px solid " overlay-background-color)}))))

(defn map-overlay-content [single-line?]
  (if single-line?
    {:display :inline-block
     :margin "0 0.5rem"
     :white-space :nowrap
     :color theme-colors/white}

    {:display :block
     :margin "0 0.5rem"
     :color theme-colors/white}))
