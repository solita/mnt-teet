(ns teet.ui.date-picker
  "Date picker component"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as t]
            [teet.ui.icons :as icons]
            [goog.events.EventType :as EventType]
            [cljs-time.format :as tf]
            [teet.localization :as localization :refer [tr tr-or]]
            [teet.ui.material-ui :refer [TextField IconButton Popover ClickAwayListener InputAdornment Typography Button]]
            [teet.ui.icons :as icons]
            [teet.localization :as localization]
            [teet.theme.theme-colors :as colors]
            [herb.core :refer [<class]]
            [teet.ui.typography :as typography]))

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

(defn date-header-class
  []
  {:text-transform :capitalize})

(defn day-header-class
  []
  {:text-align :center})

(defn date-footer-class
  []
  {:display :flex
   :justify-content :space-around
   :padding "1rem 0"})

(defn day-button-class
  [selected? cur-month?]
  (merge {:width "36px"
          :height "36px"
          :font-size "14px"}
    (when cur-month?
      {:color "rgba(0,0,0, 0.85)"})                         ;;This should be in theme colors when we get right colors
    (when selected?
      {:background-color colors/primary
       :color "white"})))

(defn date-picker
  "Date picker component.

   Options:

   :value        current selected date (goog.date.Date)
   :year         year to show (defaults to current)
   :month        month to show (defaults to current) (0 - 11)
   :on-change    callback to set new selected date
   :selectable?  fn to call for each date to check if it should be selectable"
  [{:keys [value on-change selectable?] :as opts}]
  (r/with-let [now (or value (t/now))
               showing (atom [(.getYear now) (.getMonth now)])]
    (let [{:keys [months days today]} (get locale @localization/selected-language)
          [year month] @showing
          show-month (t/date-time year (inc month) 1)
          date-in-current-month? #(same-month? show-month %)]

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
                  {:class (<class day-button-class selected? (date-in-current-month? day))
                   :on-click #(do (.stopPropagation %)
                                  (when is-selectable?
                                    (on-change day))
                                  nil)}
                  (when today?
                    {:color :primary}))
                (t/day day)]])])]]
       [:div {:class (<class date-footer-class)}
        [Button {:on-click #(do
                              (.stopPropagation %)
                              (on-change (t/now)))
                 :color :primary
                 :variant :outlined}
         today]
        [Button {:on-click #(do
                              (.stopPropagation %)
                              (on-change nil))
                 :color :secondary
                 :variant :outlined}
         (tr [:common :clear])]]])))

(defn date-input
  "Combined text field and date picker input field"
  [{:keys [label error value on-change]}]
  (r/with-let [txt (r/atom (some-> value goog.date.Date. unparse-opt))
               open? (r/atom false)
               ref (atom nil)
               close-input (fn []
                             (reset! open? false))
               open-input (fn [_]
                            (reset! open? true))
               set-ref (fn [el]
                         (reset! ref el))
               on-change-text (fn [e]
                                (let [v (-> e .-target .-value)]
                                  (reset! txt v)
                                  (if-let [^goog.date.Date d (parse-opt v)]
                                    (on-change (.-date d))
                                    (on-change nil))))]
    [:div
     [TextField {:label label
                 :value (or @txt "")
                 :ref set-ref
                 :error error
                 :variant "outlined"
                 :full-width true
                 :on-change on-change-text
                 :InputProps {:end-adornment
                              (r/as-element
                                [InputAdornment {:position :end}
                                 [IconButton {:on-click open-input
                                              :edge "end"}
                                  [icons/action-calendar-today {:color :primary}]]])}}]
     [Popover {:open @open?
               :anchorEl @ref
               :anchorOrigin {:vertical "bottom"
                              :horizontal "left"}}
      [ClickAwayListener {:on-click-away close-input}
       [date-picker {:value (when value
                              (goog.date.Date. value))
                     :on-change (fn [^goog.date.Date d]
                                  (reset! txt (unparse-opt d))
                                  (on-change (some-> d .-date))
                                  (close-input))}]]]]))
