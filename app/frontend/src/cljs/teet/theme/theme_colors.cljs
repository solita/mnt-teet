(ns teet.theme.theme-colors)

(def dark-blue "#041E42")
(def light-blue "#006db5")
(def white "#fff")

(def primary dark-blue)
(defn primary-alpha [alpha]
  (str "rgb(4, 30, 66, " alpha ")"))

(def secondary light-blue)

(def primary-text "#333333")
(def secondary-text "#53565A")
