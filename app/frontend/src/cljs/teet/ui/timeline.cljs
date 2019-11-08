(ns teet.ui.timeline
  "SVG timeline component"
  (:require [goog.date :as dt]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [Popper]]
            [taoensso.timbre :as log]
            [cljs-time.core :as t]
            [teet.theme.theme-colors :as theme-colors]
            [teet.theme.theme-panels :as theme-panels]
            [herb.core :refer [<class]]))

(defn- ->date [d]
  (if (instance? dt/Date d)
    d
    (dt/Date. d)))

(defn- year-of [^dt/Date d]
  (-> d ->date .getYear))

(defn- year-range [{:keys [start-date end-date]}]
  (range (year-of start-date) (inc (year-of end-date))))

(defn- months-from
  "Returns how many months are from the start of the given year to the given date.
  Returns fractional number where the integer part is the amount of full months and
  the fraction part is the date within the month."
  [year d]
  (let [d (->date d)
        start-of-year (t/date-time year 1 1)
        increment (if (t/before? d start-of-year) -1 1)]
    (loop [now (t/date-time year 1 1)
           months 0]
      (if (and (= (.getMonth d) (.getMonth now))
               (= (.getYear d) (.getYear now)))
        (+ months (/ (.getDate d) (.getNumberOfDaysInMonth d)))
        (do
          (.add now (dt/Interval. 0 increment))
          (recur now (+ increment months)))))))

(defn pattern-defs []
  [:defs
   [:pattern {:id "incomplete" :x 0 :y 0 :width 0.6 :height 1
              :patternUnits "userSpaceOnUse"
              :patternTransform "rotate(-45 0 0)"}
    [:rect {:x 0 :width 2 :y 0 :height 2
            :style {:fill "#f0f0f0"}}]
    [:rect {:x 0.4 :width 0.2
            :y -1 :height 2
            :style {:fill "lightgray"}}]]])
    ;[:circle {:cx 0.5 :cy 0.5 :r 1 :style {:stroke "none" :fill "#0000ff"}}]]])

;;<defs>
;;   <pattern id="pattern1"
;;            x="10" y="10" width="20" height="20"
;;            patternUnits="userSpaceOnUse" >
;;       <circle cx="10" cy="10" r="10" style="stroke: none; fill: #0000ff" />
;;   </pattern>
;; </defs>



(defn timeline [{:keys [width height
                        start-date end-date] :as opts} timeline-items]
  (r/with-let [hover (r/atom nil)]
    (if-not (and start-date end-date)
      [:div "no start or end date"]
      (let [years (year-range opts)
            num-years (count years)
            num-items (count timeline-items)
            width (or width "100%")
            height (or height (* num-items 35))
            x-start (int (* 0.4 (* 12 num-years))) ; 8
            x-of (fn [d]
                   (+ x-start
                      (months-from (first years) d)))]

        [:<>
         [:div {:style {:overflow-x "scroll"
                        :width (or width "100%")
                        :height height
                        }}
          [:svg {;:width "100%"
                 :height height

                 ;; Coordinate space:
                 ;; x is months from beginning of first year + 20 (for left hand header
                 ;; y timeline item index (first item is from 0-1)
                 :viewBox (str "0 0 " (+ x-start (* (min num-years 3) 12)) " " (inc num-items))}
           [pattern-defs]
           [:g#year-bars
            (doall
             (for [y years
                   :let [x (+ x-start (* 12 (- y (first years))))]]
               ^{:key y}
               [:g
                [:line {:x1 x :x2 x
                        :y1 0 :y2 num-items
                        :style {:stroke "black" :stroke-width 0.1}}]
                [:text {:x x :y (+ num-items 0.5)
                        :style {:font-size 0.5}} y]]))]

           [:g#items
            (doall
             (for [i (range num-items)
                   :let [{:keys [label start-date end-date fill] :as item} (nth timeline-items i)
                         start-x (x-of start-date)
                         end-x (x-of end-date)
                         y (+ i 0.1)
                         width (- end-x start-x)]]
               ^{:key i}
               [:g {:on-mouse-over #(reset! hover {:element (.-target %)
                                                   :content (:hover item)})
                    :on-mouse-out #(reset! hover nil)}
                [:text {:x 0 :y (+ y 0.7) :style {:font-size 0.8}} label]
                ;; Fill rectangles based on  status
                (loop [rects [:g]
                       i 0
                       x start-x
                       [f & fills] (if (coll? fill)
                                     fill
                                     [[1.0 fill]])]
                  (if-not f
                    rects
                    (let [[pct fill-style] f
                          w (* width pct)]
                      (recur (conj rects
                                   [:rect {:key i
                                           :x x :y y :width w :height 0.8
                                           :style {:fill fill-style}}])
                             (inc i)
                             (+ x w)
                             fills))))]))]]]
         (when-let [{:keys [element content]} @hover]
           [Popper {:open true
                    :anchorEl element
                    :placement "top"}
            [:div {:class (<class theme-panels/popup-panel)}
             content]])]))))
