(ns teet.ui.format
  "Common formatters for human readable data")

(defn date
  "Format date in human readable locale specific format, eg. dd.MM.yyyy"
  [date]
  (.toLocaleDateString date))

(defn file-size [b]
  (let [kb (/ b 1024)]
    (cond
      (> kb 1024) (str (.toFixed (/ kb 1024) 1) "mb")
      (<= kb 1) (str b "b")
      :else (str (.toFixed kb 0) "kb"))))
