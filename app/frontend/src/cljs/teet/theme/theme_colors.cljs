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
(def blue-tab "#005AA3")
(def blue-light "#007BAF")
(def blue-lighter "#A2C3DD")
(def blue-lightest (lighten blue-lighter 10))
(def orange "#FF8000")


;; Grayscale
(def gray-dark "#34394C")
(def gray "#5D6071")
(def gray-light "#8F91A8")
(def gray-lighter "#DBDFE2")
(def gray-lightest "#F2F3F3")
(def white "#FFFFFF")

(defn white-alpha [alpha]
  (str "rgba(255,255,255," alpha ")"))

(def black-coral-1 "#D2D3D8")                               ;;not sure how these should be used, straight from the design
(def black-coral "#5D6071")

(def dark-tangerine-11 "#E8A510")
(def dark-tangerine-1 "#FFF1D3")

(def mint-cream "#ECF4EF")
(def sea-green "#308653")

;; Accent colors
(def orange-light "#FF6000")
(def green "#008936")
(def red "#D73E3E")
(def red-dark "#BE2525")
(def red-lightest "#FCEEEE")
(def green-dark "#25BE25")
(def yellow "#F2C94C")

(def primary "#005AA3")
(def secondary blue-dark)
(def success green)
(def error red)
(def warning orange)

(def primary-text gray-dark)
(def secondary-text gray)

(def popup-background gray-lightest)
(def popup-border gray)

(def button-focus-style
  {:outline 0
   :box-shadow (str "0 0 0 1px" white ", "
                    "0 0 0 3px " blue-light)})

(def focus-style
  {:outline (str "2px solid " blue-light)
   :outline-offset "3px"})

;; Component colors

(def card-background-extra-light "#F9F9F9")
(def card-box-shadow "0px 2px 4px rgba(93, 96, 113, 0.36)")
(def page-background-dark "#E1E2E5")
(def border-dark "#D2D3D8")

(def background-color-light "#F0F0F2")

;; Text colors

(def text-disabled "#9799A4")

(def text-medium-emphasis "#4D4f5D")

(defn print-colors []
  (doseq [[k v] (ns-publics 'teet.theme.theme-colors)]
    (println k " => " @v)))
