(ns teet.ui.format
  "Common formatters for human readable data"
  (:require [cljs-time.format :as tf]))

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
  (when-let [[_ start-date end-date] (re-matches #"\[(\d\d\d\d-\d\d-\d\d),(\d\d\d\d-\d\d-\d\d)\)"
                                                 date-range-string)]
    (str (->> start-date tf/parse (tf/unparse formatter))
         "-"
         (->> end-date tf/parse (tf/unparse formatter)))))
