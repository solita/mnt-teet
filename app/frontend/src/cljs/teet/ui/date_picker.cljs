(ns teet.ui.date-picker
  "Date picker component"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as t]
            [cljs-time.coerce :as c]
            [teet.ui.icons :as icons]
            [cljs-time.format :as tf]
            [teet.localization :as localization :refer [tr]]
            [teet.ui.material-ui :refer [IconButton Popover ClickAwayListener
                                         Button Grid]]
            [teet.ui.text-field :refer [TextField]]
            [teet.theme.theme-colors :as colors]
            [goog.string :as gstr]
            [herb.core :refer [<class]]
            [teet.ui.format :as format]
            [clojure.string :as str]))

(defn- date-seq [first-date last-date]
  (when-not (t/after? first-date last-date)
    (lazy-seq
      (cons first-date
        (date-seq (t/plus first-date (t/days 1)) last-date)))))

(defn- split-into-weeks
  "Return sequence of 6 week from the start of the calendar."
  [year month]
  (let [month (inc month)                                   ;; cljs-time uses natural month numbers
        week-start (loop [date (t/date-time year month 1)]
                     (if (= 1 (t/day-of-week date))
                       date
                       (recur (t/minus date (t/days 1)))))]
    (partition
      7
      (date-seq week-start (t/plus week-start (t/weeks 6))))))

(defn- same-month? [d1 d2]
  (and (= (t/month d1) (t/month d2))
    (= (t/year d1) (t/year d2))))

(defn- same-day? [d1 d2]
  (and (= (t/day d1) (t/day d2))
    (same-month? d1 d2)))

(def locale {:et {:months ["jaan" "veebr" "märts" "apr" "mai" "juuni" "juuli" "aug" "sept" "okt" "nov" "dets"]
                  :days ["E" "T" "K" "N" "R" "L" "P"]
                  #_["esmaspäev" "teisipäev" "kolmapäev" "neljapäev" "reede" "laupäev" "pühapäev"]
                  :today "Täna"
                  }
             :en {:months ["jan" "feb" "mar" "apr" "may" "jun" "jul" "aug" "sep" "oct" "nov" "dec"]
                  :days ["mon" "tue" "wed" "thu" "fri" "sat" "sun"]
                  :today "Today"}})

(def format (tf/formatter "dd.MM.yyyy"))

(defn- parse [s]
  (tf/parse format s))

(defn- unparse [d]
  (tf/unparse format d))

(defn- parse-opt [s]
  (try
    (parse s)
    (catch js/Object _
      nil)))

(defn- unparse-opt [d]
  (if d
    (unparse d)
    ""))

(defn- date-header-class
  []
  {:text-transform :capitalize})

(defn- day-header-class
  []
  {:text-align :center})

(defn- date-footer-class
  []
  {:display :flex
   :justify-content :space-around
   :padding "1rem 0"})

(defn- day-button-class
  [selected? is-selectable? cur-month?]
  (merge {:width "36px"
          :height "36px"
          :font-size "14px"
          :color "rgb(0,0,0)"
          :opacity "0.5"}
    (when cur-month?
      {:opacity "0.85"})                                    ;;This should be in theme colors when we get right colors
    (when selected?
      {:background-color colors/primary
       :color "white"})
    (when-not is-selectable?
      {:color "rgb(200,0,0)"                                ;;make this material ui theme error
       :cursor :not-allowed})))

(defn date-picker
  "Date picker component.

   Options:

   :value        current selected date (goog.date.Date)
   :year         year to show (defaults to current)
   :month        month to show (defaults to current) (0 - 11)
   :on-change    callback to set new selected date
   :selectable?  fn to call for each date to check if it should be selectable"
  [{:keys [value on-change selectable? min-date] :as _opts}]
  (r/with-let [now (or value (if (and min-date
                                      (t/after? (c/from-date min-date) (t/now)))
                               (c/from-date min-date)
                               (t/now)))
               showing (atom [(.getYear now) (.getMonth now)])]
    (let [{:keys [months days today]} (get locale @localization/selected-language)
          [year month] @showing
          show-month (t/date-time year (inc month) 1)
          date-in-current-month? #(same-month? show-month %)
          selectable? (or selectable? (constantly true))]

      [:div
       [:div {:style {:display :flex
                      :justify-content :space-between
                      :align-items :center}}
        [IconButton
         {:size :small
          :on-click #(do (.preventDefault %)
                         (swap! showing
                           (fn [[year month]]
                             (if (= month 0)
                               [(dec year) 11]
                               [year (dec month)])))
                         nil)}
         [icons/navigation-chevron-left {:color :primary}]]
        [:span {:class (<class date-header-class)}
         [:span (nth months month)] " " [:span.pvm-year year]]
        [IconButton
         {:size :small
          :on-click #(do (.preventDefault %)
                         (swap! showing
                           (fn [[year month]]
                             (if (= month 11)
                               [(inc year) 0]
                               [year (inc month)])))
                         nil)}
         [icons/navigation-chevron-right {:color :primary}]]]
       [:table.date-picker {:style {:display "table"
                                    ;; Keep height stable (amount of rows changes depending on month)
                                    ;:height "200px"
                                    }}
        [:thead.date-picker-controls
         [:tr.date-picker-weekdays
          (for [day days]
            ^{:key day}
            [:td {:width "14%"
                  :class (<class day-header-class)} day])]]

        [:tbody.date-picker-dates
         (for [days (split-into-weeks year month)]
           ^{:key (str (first days))}
           [:tr
            (for [day days
                  :let [is-selectable? (or (not (some? selectable?))
                                         (selectable? day))
                        selected? (and value
                                    (= (t/day day) (t/day value))
                                    (= (t/month day) (t/month value))
                                    (= (t/year day) (t/year value)))
                        today? (and day (same-day? (t/now) day))]]
              ^{:key (str day)}

              [:td.pvm-paiva {:class (str
                                       (if is-selectable?
                                         "klikattava "
                                         "pvm-disabloitu "))}
               [IconButton
                (merge
                  {:class (<class day-button-class selected? is-selectable? (date-in-current-month? day))
                   :on-click #(do (.stopPropagation %)
                                  (when is-selectable?
                                    (on-change day))
                                  nil)}
                  (when today?
                    {:color :primary}))
                (t/day day)]])])]]
       [:div {:class (<class date-footer-class)}
        [Button (merge {:on-click #(do
                                     (.stopPropagation %)
                                     (when (selectable? (t/today))
                                       (on-change (t/today))))
                        :color :primary
                        :variant :outlined}
                  (when (not (selectable? (t/today)))
                    {:disabled true}))
         today]
        [Button {:on-click #(do
                              (.stopPropagation %)
                              (on-change nil))
                 :color :secondary
                 :variant :outlined}
         (tr [:common :clear])]]])))

(defn date-in-range?
  [{:keys [start end date]}]
  (cond
    (and (some? start) (some? end))
    (< start date end)
    (some? start)
    (< start date)
    (some? end)
    (< date end)
    :else
    true))

(defn date-input
  "Combined text field and date picker component"
  [{:keys [label error value on-change selectable? required end start min-date max-date error-text]}]
  (r/with-let [txt (r/atom (some-> value goog.date.Date. unparse-opt))
               open? (r/atom false)
               ref (atom nil)
               close-input (fn []
                             (reset! open? false))
               open-input (fn [_]
                            (reset! open? true))
               set-ref (fn [el]
                         (reset! ref el))]
    (let [on-change-text (fn [e]
                           (let [v (-> e .-target .-value)]
                             (reset! txt v)
                             (if-let [^goog.date.Date d (parse-opt v)]
                               (if (and
                                     (date-in-range? {:start min-date :end max-date :date d})
                                     (date-in-range? {:start start :end end :date d}))
                                 (on-change (.-date d))
                                 (on-change nil))
                               (on-change nil))))]
      [:div
       [TextField {:label label
                   :value (or @txt "")
                   :ref set-ref
                   :error error
                   :error-text error-text
                   :required required
                   :variant "outlined"
                   :full-width true
                   :on-change on-change-text
                   :input-button-icon icons/action-calendar-today
                   :input-button-click open-input}]
       [Popover {:open @open?
                 :anchorEl @ref
                 :anchorOrigin {:vertical "bottom"
                                :horizontal "left"}}
        [ClickAwayListener {:on-click-away close-input}
         [date-picker {:value (when value
                                (goog.date.Date. value))
                       :min-date min-date
                       :on-change (fn [^goog.date.Date d]
                                    (reset! txt (unparse-opt d))
                                    (on-change (some-> d .-date))
                                    (close-input))
                       :selectable? selectable?}]]]])))


;; Date picker clear & disable start and end
;; When first date picked enable start and end
;; When date is changed after some values in start/end call on-change to both
;; When either end or start changed call on-change with the new date value for that
;; Fail end inputs that are before start input
;; Have the ability to change start input and not have to touch the end
;; WIP in a sense that all functionality should work, but might require some fine-tuning and error support
(defn date-time-range-input
  "Give date-picker and two time inputs to form datetime rante"
  [{:keys [error value on-change required]}]
  (let [[start end] value]
    (r/with-let [time-input-value (fn [date]
                                    (when date
                                      (str (gstr/format "%02d" (.getHours date)) ":"
                                           (gstr/format "%02d" (.getMinutes date)))))
                 start-input-atom (r/atom (or (time-input-value start) ""))
                 end-input-atom (r/atom (or (time-input-value end)) "")
                 date (r/atom nil)
                 time-to-date (fn [input-val date]
                                (let [[hours minutes] (str/split input-val ":")]
                                  (when (and hours minutes)
                                    (doto (js/Date. date)
                                      (.setHours hours)
                                      (.setMinutes minutes)))))
                 date-change (fn [val]
                               (let [new-date val
                                     start-date (when (not-empty @start-input-atom)
                                                  (time-to-date @start-input-atom new-date))
                                     end-date (when (not-empty @end-input-atom)
                                                (time-to-date @end-input-atom new-date))]
                                 (reset! date val)
                                 (if (nil? val)
                                   (on-change [nil nil])
                                   (on-change [start-date end-date]))))]
      [:div
       [Grid {:container true :spacing 1}
        [Grid {:item true :xs 9}
         [date-input {:value @date
                      :label (tr [:common-texts :date])
                      :required required
                      :on-change date-change}]]
        [Grid {:item true :xs 3}
         [:div {:style {:display :flex
                        :flex-direction :row
                        :align-items :center
                        :justify-content :space-between}}
          [:div
           [TextField {:type :time
                       :required required
                       :disabled (nil? @date)
                       :label (tr [:common-texts :start-time])
                       :value @start-input-atom
                       :max @end-input-atom
                       :on-blur (fn [_]
                                  (on-change [(time-to-date @start-input-atom @date) end]))
                       :on-change (fn [e]
                                    (let [v (-> e .-target .-value)]
                                      (when (empty? v)
                                        (on-change [nil end]))
                                      (reset! start-input-atom v)))}]]
          [:p {:style {:padding "0 0.25rem"}} "–"]
          [:div
           [TextField {:type :time
                       :required required
                       :disabled (nil? @date)
                       :value @end-input-atom
                       :label (tr [:common-texts :end-time])
                       :min @start-input-atom
                       :on-blur (fn [_]
                                  (on-change [start (time-to-date @end-input-atom @date)]))
                       :on-change (fn [e]
                                    (let [v (-> e .-target .-value)]
                                      (when (empty? v)
                                        (on-change [start nil]))
                                      (reset! end-input-atom v)))}]]]]]])))

(defn date-range-input
  "combine two date-inputs to provide a consistent date-range-picker"
  [{:keys [error value on-change start-label end-label required row? min-date max-date]
    :or {row? true}}]
  (let [[start end] value
        element-size (if row?
                       6
                       12)
        max (or end max-date)
        min (or start min-date)]
    [Grid {:container true :spacing 1}
     [Grid {:item true :xs element-size}
      [date-input {:value start
                   :required required
                   :error (and error (nil? start))
                   :label start-label
                   :end end
                   :error-text (tr [:date-range :error-text] {:start (format/date min-date)
                                                              :end (format/date max)})
                   :min-date min-date
                   :max-date (or end max-date)
                   :on-change (fn [start]
                                (on-change [start end]))
                   :selectable? (fn [day]
                                  (and
                                    (if end
                                      (< (.-date day) end)
                                      true)
                                    (if min-date
                                      (<= min-date (.-date day))
                                      true)
                                    (if max-date
                                      (<= (.-date day) max-date)
                                      true)))}]]
     [Grid {:item true :xs element-size}
      [date-input {:value end
                   :required required
                   :error (and error (nil? end))
                   :label end-label
                   :start start
                   :error-text (tr [:date-range :error-text] {:start (format/date min)
                                                              :end (format/date max-date)})
                   :min-date (or start min-date)
                   :max-date max-date
                   :on-change (fn [end]
                                (on-change [start end]))
                   :selectable? (fn [day]
                                  (and
                                    (if start
                                      (< start (.-date day))
                                      true)
                                    (if min-date
                                      (<= min-date (.-date day))
                                      true)
                                    (if max-date
                                      (<= (.-date day) max-date)
                                      true)))}]]]))
