(ns teet.ui.format
  "Common formatters for human readable data"
  (:require [clojure.string :as string]
            [cljs-time.format :as tf]))

(defn date
  "Format date in human readable locale specific format, eg. dd.MM.yyyy"
  [date]
  (.toLocaleDateString date))

(defn date-time
  "Format date and time in human readable locale specific format."
  [date]
  (.toLocaleString date))

(defn file-size [b]
  (let [kb (/ b 1024)]
    (cond
      (> kb 1024) (str (.toFixed (/ kb 1024) 1) "mb")
      (<= kb 1) (str b "b")
      :else (str (.toFixed kb 0) "kb"))))

(def formatter
  (tf/formatter "dd.MM.yyyy"))

(defn date-range
  "Format string of format \"[yyyy-MM-dd,yyyy-MM-dd)\" into \"dd.MM.yyyy - dd.MM.yyyy\""
  [date-range-string]
  (when date-range-string
    (when-let [[_ start-date end-date] (re-matches #"\[(\d{4}-\d{2}-\d{2}),(\d{4}-\d{2}-\d{2})\)"
                                                   date-range-string)]
      (str (->> start-date tf/parse (tf/unparse formatter))
           "—"
           (->> end-date tf/parse (tf/unparse formatter))))))

(defn km-range
  [km-range-string]
  (when km-range-string
    (when-let [[_ start-km end-km] (re-matches #"\[(\d+\.\d+),(\d+\.\d+)\]"
                                                   km-range-string)]
      (str (string/replace start-km #"\." \,)
           " km — "
           (string/replace end-km #"\." \,)
           " km"))))
