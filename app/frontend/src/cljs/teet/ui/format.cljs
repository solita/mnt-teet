(ns teet.ui.format
  "Common formatters for human readable data"
  (:require [clojure.string :as string]
            [cljs-time.format :as tf]
            [goog.string :as gstr]
            [teet.localization :as localization]))

(defn date
  "Format date in human readable locale specific format, eg. dd.MM.yyyy"
  [date]
  (when date
    (.toLocaleDateString date "et-EE")))

(defn time*
  "Format time with minute resolution"
  [date]
  (when date
    (gstr/format "%02d:%02d" (.getHours date) (.getMinutes date))))

(defn date-time
  "Format date to a dd.mm.yyyy hh:mm"
  [date-obj]
  (when date-obj
    (str (date date-obj) " " (time* date-obj))))

(defn date-with-time-range
  [date1 date2]
  (str (date date1) " " (time* date1) " - " (time* date2)))

(defn file-size [b]
  (let [kb (/ b 1024)]
    (cond
      (> kb 1024) (str (.toFixed (/ kb 1024) 1) "mb")
      (<= kb 1) (str b "b")
      :else (str (.toFixed kb 0) "kb"))))

(def formatter
  (tf/formatter "dd.MM.yyyy"))

(defn parse-date-range
  "Parse postgres daterange \"[yyyy-MM-dd,yyyy-MM-dd)\" into a vector of two dates"
  [date-range-string]
  (when-let [[_ start-date end-date] (re-matches #"\[(\d{4}-\d{2}-\d{2}),(\d{4}-\d{2}-\d{2})\)"
                                                 date-range-string)]
    [(->> start-date tf/parse)
     (->> end-date tf/parse)]))

(defn date-range
  "Format string of format \"[yyyy-MM-dd,yyyy-MM-dd)\" into \"dd.MM.yyyy - dd.MM.yyyy\""
  [date-range-string]
  (when-let [[start-date end-date] (parse-date-range date-range-string)]
    (str (->> start-date (tf/unparse formatter))
         " — "
         (->> end-date (tf/unparse formatter)))))


(defn parse-date-string
  [string]
  (tf/unparse formatter (tf/parse string)))

(defn km-range
  [km-range-string]
  (when km-range-string
    (when-let [[_ start-km end-km] (re-matches #"\[(\d+\.\d+),(\d+\.\d+)\]"
                                                   km-range-string)]
      (str (string/replace start-km #"\." ",")
           " km — "
           (string/replace end-km #"\." ",")
           " km"))))

(defn date-string->date
  [string]
  (let [[day month year] (string/split string ".")]
    (js/Date. (str year "-" month "-" day))))

(defn localization-key-by-selected-language
  []
  (if (= @localization/selected-language :en)
    "en-US"
    "et-EE"))

(defn localized-month-year
  "Takes JS date object and returns {month-name} {Year}"
  [date]
  (let [localization-key (localization-key-by-selected-language)]
    (.toLocaleString date localization-key #js {:month "long" :year "numeric"})))

(defn localized-day-of-the-week
  "Takes js date object and returns a localized name for the day"
  [date]
  (let [localization-key (localization-key-by-selected-language)]
    (.toLocaleString date localization-key #js {:weekday "long"})))

