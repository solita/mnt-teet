(ns teet.theme.theme-colors)

(defn primary-alpha [alpha]
  (str "rgb(4, 30, 66, " alpha ")"))

;; New VEERA color theme
;; Main colors
(def blue-dark "#004665")
(def blue "#005E87")
(def blue-light "#007BAF")
(def blue-lighter "#AAE1F8")

;; Grayscale
(def gray-dark "#34394C")
(def gray "#5D6071")
(def gray-light "#8F91A8")
(def gray-lighter "#DBDFE2")
(def gray-lightest "#F2F3F3")
(def white "#FFFFFF")

;; Accent colors
(def orange-light "#FF8000")
(def green "#008936")
(def red "#D73E3E")

(def primary blue-light)
(def secondary blue-dark)
(def success green)
(def error red)

(def primary-text gray-dark)
(def secondary-text gray)
