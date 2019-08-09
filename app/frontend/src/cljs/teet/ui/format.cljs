(ns teet.ui.format
  "Common formatters for human readable data")

(defn date
  "Format date in human readable locale specific format, eg. dd.MM.yyyy"
  [date]
  (.toLocaleDateString date))
