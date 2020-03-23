(ns teet.ui.timeline
  "SVG timeline component"
  (:require [goog.date :as dt]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [Popover Popper]]
            [cljs-time.core :as t]
            [teet.theme.theme-colors :as theme-colors]
            [teet.theme.theme-panels :as theme-panels]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            [clojure.string :as str]))

(defn- ->date [d]
  (if (instance? dt/Date d)
    d
    (dt/Date. d)))

(defn- year-of [^dt/Date d]
  (-> d ->date .getYear))

(defn- year-range [{:keys [start-date end-date]}]
  (range (year-of start-date) (+ 2 (year-of end-date))))

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

(defn- timeline-items-group [{:keys [x-of y-of hover line-height timeline-items]}]
  [:g#items
   (doall
    (for [i (range (count timeline-items))
          :let [{:keys [start-date end-date colors label] :as item} (nth timeline-items i)
                {fill :background text-color :text} colors
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
             (recur (-> rects
                        (conj [:rect {:key i
                                      :x x :y y :width w :height height
                                      :style {:fill fill-style}}])
                        (conj [:text {:key (str i "-text")
                                      :fill text-color
                                      :x (+ x 6) :y (+ y (* height 0.8))}
                               label]))
                    (inc i)
                    (+ x w)
                    fills))))]))])

(defn- today-group
  "Display a vertical bar and label for current date."
  [{:keys [x-of y-of line-width line-height num-items]}]
  (let [x (x-of (t/now))
        y (y-of (+ 1.5 num-items))]
    [:g#today
     [:line {:x1 x :x2 x
             :y1 0 :y2 y
             :style {:stroke theme-colors/red-dark :stroke-width line-width
                     :stroke-dasharray "3 1"}}]
     [:text {:x x :y (+ y (/ line-height 1.25))
             :text-anchor "middle"}
      (tr [:common :now])]]))

(defn- container-style []
  {:display "flex"
   :flex-direction "row"})
(defn- labels-style [y-start]
  {:display "flex"
   :flex-direction "column"
   :padding-top (str y-start "px")
   :padding-right "0.5rem"})

(defn- label-style []
  {:display "inline-block"
   :white-space "nowrap"
   :cursor "pointer"})
(defn- bars-style []
  {:overflow-x "scroll"})

(defn- on-scroll [e]
  ;; Handler to prevent scrolling when mouse is inside the timeline-bars
  (.stopPropagation e)
  (.preventDefault e))

(defn- scroll-view [e]
  ;; Find "timeline-bars" element from event, and scroll it horizontally
  (loop [elt (.-target e)]
    (when-not (or (nil? elt)
                  (= elt js/document.body))
      (let [c (.getAttribute elt "class")]
        (if (and c (str/starts-with? c "timeline-bars"))
          (set! (.-scrollLeft elt) (+ (.-deltaX e) (.-scrollLeft elt)))

          (recur (.-parentElement elt)))))))

(defn- year-bars-group [{:keys [x-of y-of line-width years num-items]}]
  [:g.timeline-year-bars
   (doall
    (for [year years
          :let [x (x-of (t/date-time year 1 1))
                y (y-of (inc num-items))]]
      ^{:key year}
      [:g
       [:line {:x1 x :x2 x
               :y1 0 :y2 y
               :style {:stroke theme-colors/blue-dark :stroke-width line-width}}]
       [:text {:x (+ x 5) :y y} year]]))])

(defn- month-labels-group [{:keys [x-of years month-width]}]
  [:g.timeline-month-labels
   (when (> month-width 20)
     (doall
      (for [year years
            month (range 1 13)
            :let [x (+ 5 ;(/ month-width 2)
                       (x-of (t/date-time year month)))]]
        ^{:key (str year "/" month)}
        [:text {:x x :y 15}
         (if (> month-width 50)
           (tr [:calendar :months (dec month)])
           (str month))])))])

(def ^:const animation-duration-ms 160)

(defn- animate! [elt prop-name to]
  (let [from (aget elt prop-name)
        animate! (fn animate! [start-ms now]
                   (let [start-ms (or start-ms now)]
                     (if (>= now (+ start-ms animation-duration-ms))
                       (aset elt prop-name to)
                       (do
                         (aset elt prop-name
                               (+ from (* (/ (- now start-ms) animation-duration-ms) (- to from))))
                         (.requestAnimationFrame js/window (partial animate! start-ms))))))]
    (.requestAnimationFrame js/window
                            (partial animate! nil))))

(def colors
  {:project {:background theme-colors/blue
             :text       theme-colors/white}
   :lifecycle {:background theme-colors/blue-light
               :text       theme-colors/white}
   :activity {:background theme-colors/blue-lighter
              :text       theme-colors/blue-dark}
   :subtask {:background theme-colors/gray}})

(defn timeline [{:keys [start-date end-date
                        month-width
                        line-width]
                 :as opts
                 :or {month-width 20
                      line-width 2}}
                timeline-items]
  (r/with-let [hover (r/atom nil)
               line-height (r/atom nil)
               set-line-height! #(when %
                                   (->> % .-clientHeight (reset! line-height)))
               initial-month-width month-width
               month-width (r/atom month-width)
               on-wheel (fn [e]
                          (if (> (Math/abs (.-deltaY e))
                                 (Math/abs (.-deltaX e)))
                            (swap! month-width
                                   (fn [old-width]
                                     (max 5
                                          (min 250
                                               (+ old-width (int (/ (.-deltaY e) 2)))))))
                            (scroll-view e)))
               bars-id (gensym "timeline-bars")
               scroll-left! #(animate! (js/document.getElementById bars-id)
                                       "scrollLeft" %)

               disable-window-scroll #(.addEventListener js/window "wheel" on-scroll
                                                         #js {:passive false})
               enable-window-scroll #(.removeEventListener js/window "wheel" on-scroll
                                                           #js {:passive false})

               x-start 25
               y-start 25]
    (if-not (and start-date end-date)
      [:span.timeline-no-start-or-end]
      (let [years (year-range opts)
            num-items (count timeline-items)
            x-of (fn [d]
                   (+ x-start
                      (* @month-width
                         (months-from (first years) d))))
            line-height @line-height
            y-of (fn [i]
                   (+ y-start
                      (* line-height i)))]

        [:div {:class (<class container-style)}
         [:div.timeline-labels {:class (<class labels-style y-start)}
          (doall
           (for [i (range num-items)
                 :let [{label :label start-date :start-date} (nth timeline-items i)]]
             ^{:key i}
             [:div (merge
                    {:class (<class label-style)
                     :on-click #(scroll-left! (x-of start-date))}
                    (when (zero? i)
                      {:ref set-line-height!}))
              label]))]
         [:div.timeline-bars {:class (<class bars-style)
                              :on-wheel on-wheel
                              :on-mouse-enter disable-window-scroll
                              :on-mouse-leave enable-window-scroll
                              :id bars-id}

          (when line-height
            [:svg {:width (x-of (t/plus (t/date-time end-date) (t/years 5)))
                   :height (y-of (+ 3 num-items))}
             [pattern-defs line-height initial-month-width line-width]
             [year-bars-group {:x-of x-of
                               :y-of y-of
                               :num-items num-items
                               :line-width line-width
                               :years years}]
             [month-labels-group {:x-of x-of
                                  :years years
                                  :month-width @month-width}]

             [timeline-items-group {:x-of x-of
                                    :y-of y-of
                                    :hover hover
                                    :timeline-items timeline-items
                                    :line-height line-height}]

             [today-group {:x-of x-of
                           :y-of y-of
                           :line-width line-width
                           :line-height line-height
                           :num-items num-items}]])]

         (when-let [{:keys [element content]} @hover]
           [Popper {:open true
                    :anchorEl element
                    :placement "top"
                    :modifiers #js {:hide #js {:enabled false}
                                    :preventOverflow #js {:enabled false}}
                    :disable-portal true}
            [:div {:class (<class theme-panels/popup-panel :bottom)}
             content]])]))))
