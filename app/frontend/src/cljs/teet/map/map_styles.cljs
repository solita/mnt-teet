(ns teet.map.map-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn map-controls
  ([] (map-controls {}))
  ([{:keys [position right]
     :or {position :top
          right "80px"}}]
   (merge
    {:background-color "white"
     :max-width "40vw"
     :position :absolute
     :right right
     :z-index 999
     :box-shadow "0px 2px 8px rgba(0, 0, 0, 0.25)"}
    (case position
      :top {:top "25px"}
      :bottom {:bottom "25px"
               :min-width "250px"
               :right "25px"}))))

(defn map-layer-controls []
  (map-controls {:position :bottom}))

(defn map-legend-controls []
  (map-controls {:position :top}))

(defn map-layer-controls-body []
  {:padding "0.5rem"})

(defn add-layer-button []
  {:width "100%"})

(defn edit-layer-type []
  {:padding "1rem"
   :background-color theme-colors/gray
   :color theme-colors/white
   :height "100%"})

(defn edit-layer-form []
  {:padding "1.5rem"
   :display :flex
   :flex-direction :column
   :min-height "40vh"})

(defn edit-layer-type-heading [selected?]
  {:cursor :pointer
   :margin-bottom "1rem"
   :color (if selected?
            theme-colors/white
            theme-colors/gray-lighter)})

(defn edit-layer-options []
  {:flex-grow 1
   :padding "1rem"
   :margin-bottom "1rem"
   :background-color theme-colors/gray-lightest})

(defn map-controls-heading
  []
  {:display :flex
   :justify-content :space-between
   :align-items :center
   :padding "1rem"
   :margin 0
   :background theme-colors/gray-lighter
   :border-bottom (str "1px solid " theme-colors/gray-light)})

(defn layer-edit-save-style
  []
  {:margin-left "1rem"})

(defn layer-heading-style
  []
  {:padding-bottom "1rem"})

(defn layer-edit-button-container-style
  []
  {:display :flex
   :justify-content :flex-end})

(defn map-control-buttons
  []
  {:position :absolute
   :display  :flex
   :flex-direction :row
   :top      "20px"
   :right    "25px"
   :z-index  999})

(defn map-legend-header []
  {:background-color theme-colors/gray-lighter
   :font-weight :bold})

(defn map-legend-box []
  {:background-color theme-colors/gray-lightest
   :margin "0.5rem"
   :padding "0.5rem"
   :max-height "50vh"
   :overflow-y :scroll})

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

(defn map-overlay-container
  [width height arrow-direction background-color]
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
     {:background-color background-color
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
