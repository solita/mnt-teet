(ns teet.map.map-features
  "Defines rendering styles for map features "
  (:require [clojure.string :as str]
            [ol.style.Style]
            [ol.style.Icon]
            [ol.style.Stroke]
            [ol.style.Fill]
            [ol.render.Feature]
            [ol.style.Circle]
            [teet.theme.theme-colors :as theme-colors]))

(def ^:const map-pin "M9.96587 0.5C15.221 0.5 19.5 4.78348 19.5 9.96587C19.5 12.7235 17.9724 15.4076 15.9208 18.0187C14.9006 19.3172 13.7685 20.5764 12.6603 21.802C12.611 21.8565 12.5618 21.911 12.5126 21.9653C11.6179 22.9546 10.7407 23.9244 9.9694 24.8638C9.1882 23.8963 8.29237 22.8969 7.37848 21.8774L7.31238 21.8036C6.21334 20.5775 5.08749 19.3183 4.07125 18.0195C2.02771 15.4079 0.5 12.7237 0.5 9.96587C0.5 4.78126 4.78126 0.5 9.96587 0.5Z")
(def ^:const map-pin-height 26)
(def ^:const map-pin-width 20)

(def project-pin-icon
  (memoize
    (fn [w h fill stroke center-fill]
      (let [canvas (.createElement js/document "canvas")
            scaled-w (/ w map-pin-width)
            scaled-h (/ h map-pin-height)]
        (set! (.-width canvas) w)
        (set! (.-height canvas) h)
        (let [ctx (.getContext canvas "2d")]
          (.scale ctx scaled-w scaled-h)
          (when fill
            (set! (.-strokeStyle ctx) stroke)
            (set! (.-fillStyle ctx) fill)
            ;; draw the map pin
            (let [p (js/Path2D. map-pin)]
              (.stroke ctx p)
              (.fill ctx p)))
          ;; draw the center white circle in the pin
          (when center-fill
            (set! (.-strokeStyle ctx) stroke)
            (set! (.-fillStyle ctx) center-fill)
            (.arc ctx 10 10 4 0 (* 2 js/Math.PI))
            (.stroke ctx)
            (.fill ctx)))
        canvas))))


(def road-buffer-fill-style
  (ol.style.Style.
    #js {:stroke (ol.style.Stroke. #js {:color "rgba(0,0,255,0.6)"
                                       :width 1})
         :fill (ol.style.Fill. #js {:color "rgba(0,0,255,0.2)"})}))

(defn road-line-style [color ^ol.render.Feature _feature res]
  (let [line-width (+ 3 (min 5 (int (/ 200 res))))]
    ;; Show project road geometry line
    (ol.style.Style.
      #js {:stroke (ol.style.Stroke. #js {:color color
                                          :width line-width
                                          :lineCap "butt"})
           :zIndex 2})))

(def ^{:doc "Show project geometry as the road line."} project-line-style
  (partial road-line-style "blue"))


(def electric-pattern
  (let [a (atom nil)
        canvas (.createElement js/document "canvas")]
    (set! (.-width canvas) 32)
    (set! (.-height canvas) 32)
    (let [ctx (.getContext canvas "2d")]
      (set! (.-strokeStyle ctx) "black")
      (set! (.-lineWidth ctx) 3)
      (set! (.-fillStyle ctx) "yellow")
      (.fillRect ctx 0 0 32 32)

      ;; Draw electricity lightning arrow symbol
      (.scale ctx 0.0625 0.0625)
      (.stroke ctx (js/Path2D. "M174.167,512l67.63-223.176H101.992L218.751,0h134.235l-86.647,213.73h143.669L174.167,512z M127.689,271.495h137.467l-47.9,158.072l156.959-198.508H240.614l86.647-213.73h-96.824L127.689,271.495z"))

      (let [img (js/Image.)]
        (set! (.-onload img) #(do
                                (js/console.log "img loaded" img)
                                (reset! a (.createPattern ctx img "repeat"))))
        (set! (.-src img) (.toDataURL canvas))))
    a))


(defn- restriction-fill [feature default-color]
  (let [[type & _] (some-> feature .getProperties (aget "tooltip") (str/split #":"))]
    (if (= type "Elektripaigaldise kaitsevöönd")
      @electric-pattern
      default-color)))

(defn project-related-restriction-style
  "Show project related restriction as a filled area."
  [^ol.render.Feature feature _res]
  (let [hover? (.get feature "hover")]
    (ol.style.Style.
      #js {:stroke (ol.style.Stroke. #js {:color "rgba(143,0,255,0.8)"
                                          :width (if hover?
                                                   3
                                                   1)})
           :fill   (ol.style.Fill. #js {:color (if hover?
                                                 "rgba(143,0,255,0.5)"
                                                 (restriction-fill feature "rgba(143,0,255,0.2)"))})
           :zIndex 3})))

(defn project-restriction-style
  "Show restriction geometrys as area. Restrictions are all (multi)polygons."
  [^ol.render.Feature feature _res]
  (ol.style.Style.
    #js {:stroke (ol.style.Stroke. #js {:color "rgba(143,0,255,0.8)"
                                        :width 1})
         :fill   (ol.style.Fill. #js {:color "rgba(143,0,255,0.2)"})
         :zIndex 3}))

(defn project-pin-style
  "Show project centroid as a pin icon."
  [^ol.render.Feature _feature _res]
  (ol.style.Style.
    #js {:image (ol.style.Icon.
                  #js {:img (project-pin-icon map-pin-width
                                              map-pin-height
                                              theme-colors/primary
                                              theme-colors/blue-dark
                                              theme-colors/white)
                       :imgSize #js [24 30]
                       :anchor #js [0.5 1.0]})}))

(defn crosshair-pin-style
  [^ol.render.Feature _feature _res]
  (ol.style.Style.
    #js {:image (ol.style.Icon.
                  #js {:src "/img/crosshair.png"
                       :anchor #js [0.5 0.5]})}))

(defn cadastral-unit-style
  "Show cadastral unit."
  [^ol.render.Feature feature res]
  (let [hover? (.get feature "hover")]
    (ol.style.Style.
      #js {:stroke (ol.style.Stroke. #js {:color    "rgba(0,0,0,0.6)"
                                          :lineDash #js [(/ 15 res), (/ 15 res)] ;;TOdo fix issues with zooming
                                          :width    2})
           :fill   (ol.style.Fill. #js {:cursor :pointer
                                        :color  (if hover?
                                                  "rgba(100,110,105,0.6)"
                                                  "rgba(186,187,171,0.6)")})})))

(defn selected-cadastral-unit-style
  "style for selected cadastral units"
  [^ol.render.Feature _feature _res]
  (ol.style.Style.
    #js {:stroke (ol.style.Stroke. #js {:color "rgba(0,94,135,0.8)"
                                        :width 2})
         :fill   (ol.style.Fill. #js {:cursor :pointer
                                      :color  "rgba(0,94,135,0.5)"})}))

(defn selected-restrictions-style
  "Show project related restriction as a filled area."
  [^ol.render.Feature _feature _res]
  (ol.style.Style.
    #js {:stroke (ol.style.Stroke. #js {:color "rgba(0,94,135,0.8)"
                                        :width 2})
         :fill   (ol.style.Fill. #js {:cursor :pointer
                                      :color  "rgba(0,94,135,0.5)"})}))


(defn survey-style
  "Show survey area."
  [^ol.render.Feature feature _res]
  (ol.style.Style.
   #js {:stroke (ol.style.Stroke. #js {:color "#830891"
                                       :width 2})
        :fill (ol.style.Fill. #js {:cursor :pointer
                                   :color "#af38bc"})}))

(defn ags-survey-style
  "Show AGS survey feature (point)"
  [^ol.render.Feature feature _res]
  (ol.style.Style.
   #js {:image (ol.style.Circle.
                #js {:fill (ol.style.Fill. #js {:color "red"})
                     :stroke (ol.style.Stroke. #js {:color "black"})
                     :radius 5})}))
