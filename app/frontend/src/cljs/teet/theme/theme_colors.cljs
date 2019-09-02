(ns teet.theme.theme-colors)

(def dark-blue "#041E42")
(def blue "#002f87")
(def light-blue "#006db5")

(def white "#fff")

(def green "#43a047")

(def dark-red "#91001D")

(def primary dark-blue)
(defn primary-alpha [alpha]
  (str "rgb(4, 30, 66, " alpha ")"))

(def secondary light-blue)
(def success green)
(def error dark-red)

(def primary-text "#333333")
(def secondary-text "#53565A")

(def gray100 "#d9d9d6")
