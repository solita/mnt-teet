(ns teet.ui.timeline
  "SVG timeline component"
  (:require [goog.date :as dt]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [Popper]]
            [cljs-time.core :as t]
            [teet.theme.theme-panels :as theme-panels]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]))

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

(defn pattern-defs [line-height month-width line-width]
  [:defs
   [:pattern {:id "incomplete"
              :x 0 :y 0
              :width month-width
              :height line-height
              :patternUnits "userSpaceOnUse"
              :patternTransform "rotate(-45 0 0)"}
    [:rect {:x 0 :width month-width :y 0 :height (* 2 line-height)
            :style {:fill "#c0c0c0"}}]
    [:line {:x1 (/ month-width 2) :y1 0
            :x2 (/ month-width 2) :y2 line-height
            :style {:stroke "lightgray" :stroke-width (* 3 line-width)}}]]])


(defn timeline [{:keys [width height
                        start-date end-date
                        month-width
                        line-width]
                 :as opts
                 :or {month-width 20
                      line-width 2}}
                timeline-items]
  (r/with-let [hover (r/atom nil)
               line-height (r/atom nil)
               set-line-height! #(when %
                                   (->> % .-clientHeight (reset! line-height)))]
    (if-not (and start-date end-date)
      [:div "no start or end date"]
      (let [years (year-range opts)
            num-items (count timeline-items)
            width (or width "100%")
            height (or height (* (inc num-items) 35))
            x-start 10 ;(int (* 0.4 (* 12 num-years)))
            x-of (fn [d]
                   (+ x-start
                      (* month-width
                         (months-from (first years) d))))
            line-height @line-height
            y-of (fn [i]
                   (* line-height i))]

        [:div {:style {:display "flex" :flex-direction "row"}}
         [:div.timeline-labels {:style {:display "flex" :flex-direction "column"}}
          (doall
           (for [i (range num-items)
                 :let [{label :label} (nth timeline-items i)]]
             ^{:key i}
             [:div (merge
                    {:style {:display "inline-block"
                             :white-space "nowrap"}}
                    (when (zero? i)
                      {:ref set-line-height!}))
              label]))]
         [:div.timeline-bars {:style {:overflow-x "scroll"
                                      :width width
                                      :height height}}

          (when line-height
            [:svg {:width (x-of (t/plus (t/date-time end-date) (t/years 5)))}
             [pattern-defs line-height month-width line-width]
             [:g#year-bars
              (doall
               (for [year years
                     :let [x (x-of (t/date-time year 1 1))
                           y (y-of (inc num-items))]]
                 ^{:key y}
                 [:g
                  [:line {:x1 x :x2 x
                          :y1 0 :y2 y
                          :style {:stroke "black" :stroke-width line-width}}]
                  [:text {:x (+ x (/ month-width 4)) :y y} year]]))]

             [:g#items
              (doall
               (for [i (range num-items)
                     :let [{:keys [start-date end-date fill] :as item} (nth timeline-items i)
                           start-x (x-of start-date)
                           end-x (x-of end-date)
                           y (+ (y-of i) (* 0.1 line-height))
                           width (- end-x start-x)
                           height (* 0.8 line-height)]]
                 ^{:key i}
                 [:g {:on-mouse-over #(reset! hover {:element (.-target %)
                                                     :content (:hover item)})
                      :on-mouse-out #(reset! hover nil)}
                  ;; Fill rectangles based on status
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
                                             :x x :y y :width w :height height
                                             :style {:fill fill-style}}])
                               (inc i)
                               (+ x w)
                               fills))))]))]

             (let [x (x-of (t/now))
                   y (y-of (+ 1.5 num-items))]
               [:g#today
                [:line {:x1 x :x2 x
                        :y1 0 :y2 y
                        :style {:stroke "red" :stroke-width line-width
                                :stroke-dasharray "3 1"}}]
                [:text {:x x :y (+ y (/ line-height 2))
                        :text-anchor "middle"}
                 (tr [:common :now])]])])]
         (when-let [{:keys [element content]} @hover]
           [Popper {:open true
                    :anchorEl element
                    :placement "top"}
            [:div {:class (<class theme-panels/popup-panel)}
             content]])]))))
