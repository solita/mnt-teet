(ns teet.theme.theme-colors
  (:require [garden.color :refer [lighten]]))

(defn primary-alpha [alpha]
  (str "rgb(4, 30, 66, " alpha ")"))

;; New VEERA color theme
;; Main colors
(def blue-dark "#004665")
(def blue "#005E87")
(defn blue-alpha [alpha]
  (str "rgb(0, 94, 135, " alpha ")"))
(def blue-light "#007BAF")
(def blue-lighter "#AAE1F8")
(def blue-lightest (lighten blue-lighter 10))
(def orange "#FF8000")

;; Grayscale
(def gray-dark "#34394C")
(def gray "#5D6071")
(def gray-light "#8F91A8")
(def gray-lighter "#DBDFE2")
(def gray-lightest "#F2F3F3")
(def white "#FFFFFF")

;; Accent colors
(def orange-light "#FF6000")
(def green "#008936")
(def red "#D73E3E")
(def red-dark "#BE2525")
(def green-dark "#25BE25")
(def yellow "#F2C94C")

(def primary blue)
(def secondary blue-dark)
(def success green)
(def error red)
(def warning orange)

(def primary-text gray-dark)
(def secondary-text gray)

(def popup-background gray-lightest)
(def popup-border gray)

(def focus-style
  {:outline 0
   :box-shadow (str "0 0 0 1px" white ", "
                    "0 0 0 3px " blue-light)})
