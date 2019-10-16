(ns teet.map.map-features
  "Defines rendering styles for map features "
  (:require [clojure.string :as str]
            [ol.style.Style]
            [ol.style.Icon]
            [ol.style.Stroke]
            [ol.style.Fill]
            [ol.render.Feature]))

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
  (ol.style.Style.
   #js {:fill (ol.style.Fill. #js {:color (restriction-fill feature "#f26060")})
        :zIndex 3}))

(defn project-restriction-style
  "Show restriction geometrys as area. Restrictions are all (multi)polygons."
  [^ol.render.Feature feature _res]
  (ol.style.Style.
   #js {:stroke (ol.style.Stroke. #js {:color "rgba(255,0,0,90)"
                                       :width 2})
        :fill (ol.style.Fill. #js {:color (restriction-fill feature "rgba(200,50,50, 0.20)")})}))

(defn project-pin-style
  "Show project centroid as a pin icon."
  [^ol.render.Feature _feature _res]
  (ol.style.Style.
   #js {:image (ol.style.Icon.
                #js {:src "/img/pin.png"
                     :anchor #js [0.5 1.0]})}))

(defn crosshair-pin-style
  [^ol.render.Feature _feature _res]
  (ol.style.Style.
   #js {:image (ol.style.Icon.
                #js {:src "/img/crosshair.png"
                     :anchor #js [0.5 0.5]})}))

(defn cadastral-unit-style
  "Show cadastral unit."
  [^ol.render.Feature _feature _res]
  (ol.style.Style.
   #js {:stroke (ol.style.Stroke. #js {:color "rgba(40,40,255,90)"
                                       :width 2})
        :fill (ol.style.Fill. #js {:color "rgba(40,40,255, 0.20)"})}))
