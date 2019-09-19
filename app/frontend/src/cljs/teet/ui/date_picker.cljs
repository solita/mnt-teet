(ns teet.ui.date-picker
  "Date picker component"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as t]
            [teet.ui.icons :as icons]
            [goog.events.EventType :as EventType]
            [cljs-time.format :as tf]
            [teet.ui.material-ui :refer [TextField IconButton Popover]]
            [teet.ui.icons :as icons]
            [teet.localization :as localization]))

(defn- date-seq [first-date last-date]
  (when-not (t/after? first-date last-date)
    (lazy-seq
     (cons first-date
           (date-seq (t/plus first-date (t/days 1)) last-date)))))

(defn- split-into-weeks
  "Return sequence of 6 week from the start of the calendar."
  [year month]
  (let [month (inc month) ;; cljs-time uses natural month numbers
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
               nayta (atom [(.getYear now) (.getMonth now)])]
    (let [{:keys [months days today]} (get locale @localization/selected-language)
          [year month] @nayta
          show-month (t/date-time year (inc month) 1)
          show-month-day? #(same-month? show-month %)]
      [:table.date-picker {:style {:display "table"
                                   ;; Keep height stable (amount of rows changes depending on month)
                                   :height "200px"}}
       [:thead.date-picker-controls
        [:tr
         [:td.date-picker-prev-month.clickable
          {:on-click #(do (.preventDefault %)
                          (swap! nayta
                                 (fn [[year month]]
                                   (if (= month 0)
                                     [(dec year) 11]
                                     [year (dec month)])))
                          nil)}
          (icons/navigation-chevron-left)]
         [:td {:col-span 5} [:span.pvm-kuukausi (nth months month)] " " [:span.pvm-year year]]
         [:td.date-picker-next-month.clickable
          {:on-click #(do (.preventDefault %)
                          (swap! nayta
                                 (fn [[year month]]
                                   (if (= month 11)
                                     [(inc year) 0]
                                     [year (inc month)])))
                          nil)}
          (icons/navigation-chevron-right)]]
        [:tr.date-picker-weekdays
         (for [day days]
           ^{:key day}
           [:td {:width "14%"} day])]]

       [:tbody.date-picker-dates
        (for [paivat (split-into-weeks year month)]
          ^{:key (str (first paivat))}
          [:tr
           (for [paiva paivat
                 :let [valittava? (or (not (some? selectable?))
                                      (selectable? paiva))]]
             ^{:key (str paiva)}
             [:td.pvm-paiva {:class (str
                                     (if valittava?
                                       "klikattava "
                                       "pvm-disabloitu ")
                                     (let [now (t/now)]
                                       (when (and value (same-day? now value)) "pvm-tanaan "))
                                     (when (and value
                                                (= (t/day paiva) (t/day value))
                                                (= (t/month paiva) (t/month value))
                                                (= (t/year paiva) (t/year value)))
                                       "pvm-valittu ")
                                     (if (show-month-day? paiva)
                                       "pvm-show-month-paiva" "pvm-muu-month-paiva"))

                             :on-click #(do (.stopPropagation %)
                                            (when valittava?
                                              (on-change paiva))
                                            nil)}
              (t/day paiva)])])]
       [:tfoot.pvm-tanaan-text
        [:tr [:td {:colSpan 7}
              [:a {:on-click #(do
                                (.preventDefault %)
                                (.stopPropagation %)
                                (on-change (t/now)))}
               today]]]]])))

(defn date-input
  "Combined text field and date picker input field"
  [{:keys [label value on-change]}]
  (r/with-let [txt (r/atom (unparse-opt value))
               open? (r/atom false)
               ref (atom nil)]
    [:<>
     [TextField {:label label
                 :value @txt
                 :ref #(reset! ref %)
                 :variant "outlined"
                 :on-change #(let [v (-> % .-target .-value)]
                               (reset! txt v)
                               (when-let [^goog.date.Date d (parse-opt v)]
                                 (on-change (.-date d))))}]
     [IconButton {:on-click #(reset! open? true)}
      [icons/action-calendar-today]]
     [Popover {:open @open?
               :anchorEl @ref
               :anchorOrigin {:vertical "bottom"
                              :horizontal "left"}}
      [date-picker {:value (when value
                             (goog.date.Date. value))
                    :on-change (fn [^goog.date.Date d]
                                 (reset! txt (unparse-opt d))
                                 (on-change (.-date d))
                                 (reset! open? false))}]]]))
