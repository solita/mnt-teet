(ns teet.ui.date-picker
  "Date picker component"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as t]
            [teet.ui.icons :as icons]
            [goog.events.EventType :as EventType]
            [cljs-time.format :as tf]
            [teet.ui.material-ui :refer [TextField IconButton Popover ClickAwayListener InputAdornment Typography Button]]
            [teet.ui.icons :as icons]
            [teet.localization :as localization]
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

(defn date-header
  []
  {:text-transform :capitalize})

(defn day-header
  []
  {:text-align :center})

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
          show-month-day? #(same-month? show-month %)]

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
        [:span {:class (<class date-header)}
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
                  :class (<class day-header)} day])]]

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
                                         "pvm-disabloitu ")
                                       (if (show-month-day? day)
                                         "pvm-show-month-paiva" "pvm-muu-month-paiva"))}
               [IconButton
                (merge
                  {:style {:width "36px"
                           :height "36px"}
                   :on-click #(do (.stopPropagation %)
                                  (when is-selectable?
                                    (on-change day))
                                  nil)}
                  (when selected?
                    {:style {:width "36px"
                             :height "36px"
                             :background-color teet.theme.theme-colors/primary
                             :color "white"}})
                  (when today?
                    {:color :primary}))
                [typography/Text
                 (t/day day)]]])])]
        [:tfoot.pvm-tanaan-text
         [:tr [:td {:colSpan 7}
               [Button {:on-click #(do
                                     (.stopPropagation %)
                                     (on-change (t/now)))}
                today]]]]]])))

(defn date-input
  "Combined text field and date picker input field"
  [{:keys [label value on-change]}]
  (r/with-let [txt (r/atom (some-> value goog.date.Date. unparse-opt))
               open? (r/atom false)
               ref (atom nil)
               close-input (fn [_]
                             (reset! open? false))
               open-input (fn [_]
                            (reset! open? true))
               set-ref (fn [el]
                         (reset! ref el))]
    [:<>
     [TextField {:label label
                 :value (or @txt "")
                 :ref set-ref
                 :variant "outlined"
                 :full-width true
                 :on-change #(let [v (-> % .-target .-value)]
                               (reset! txt v)
                               (when-let [^goog.date.Date d (parse-opt v)]
                                 (on-change (.-date d))))
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
                                  (on-change (.-date d))
                                  (close-input))}]]]]))
