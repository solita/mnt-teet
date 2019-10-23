(ns teet.navigation.navigation-style
  (:require [teet.theme.theme-spacing :as theme-spacing]
            [teet.theme.theme-colors :as theme-colors]))

(def drawer-width {true 200   ; width when open
                   false 80}) ; width when closed

(defn drawer
  [open?]
  (let [w (drawer-width open?)]
    {:min-width (str w "px")
     :width (str w "px")
     :transition "all 0.2s ease-in-out"}))

(defn toolbar
  []
  {:display :flex
   :justify-content :space-around
   :min-height theme-spacing/appbar-height})

(defn maanteeamet-logo
  []
  {:background-color theme-colors/white
   :display "flex"
   :height "90px"
   :align-items "center"
   :justify-content "center"})

(defn drawer-footer
  []
  {:margin-top "auto"
   :padding "1rem 0"
   :display :flex
   :justify-content :center})

(defn page-listing
  []
  {:padding "0"})

(defn appbar []
  {:background-color theme-colors/white
   :color theme-colors/gray-dark
   :box-shadow "0px 2px 4px rgba(0, 0, 0, 0.36)"
   :transition "all 0.2s ease-in-out"})

(def appbar-height "90px")

(defn appbar-position [drawer-open?]
  (let [dw (drawer-width drawer-open?)]
    {:z-index 10
     :height appbar-height
     :width (str "calc(100% - " dw "px)")
     :margin-left (str dw "px")}))

(defn main-container [drawer-open?]
  (let [dw (drawer-width drawer-open?)]
    {:z-index 10
     ;; :padding "0 24px"
     :width (str "calc(100% - " dw "px)")
     :transition "all 0.2s ease-in-out"
     :margin-left (str dw "px")}))

(defn drawer-projects-style
  []
  {:text-transform :uppercase})

(defn drawer-list-item-style
  [current-page?]
  ^{:pseudo {:last-child {:border-width "1px"}
             :hover {:background-color theme-colors/blue-light}}}
  {:border-color theme-colors/blue-dark
   :border-width "1px 1px 0 1px"
   :border-style "solid"
   :padding "1rem"
   :white-space :nowrap
   :display :flex
   :flex-direction :column
   :align-items :center
   :justify-content :center
   :min-height appbar-height
   :transition "background-color 0.2s ease-in-out"
   :background-color (if current-page?
                       theme-colors/blue-light
                       theme-colors/blue)})

(defn language-select-container-style
  []
  {:display :flex
   :flex-direction :row
   :justify-content :flex-end
   :flex-grow 1
   :flex-basis "10%"})

(defn language-select-style
  []
  {:color theme-colors/blue
   :padding-bottom 0
   :border "none"})

;; A rather painful way to imitate the Material UI label + input style
(defn user-info-style
  []
  {:border 0
   :margin 0
   :display :inline-flex
   :padding 0
   :position :relative
   :min-width 0
   :flex-direction :column
   :vertical-align :top})

(defn user-label-style
  []
  {:top 0
   :left 0
   :position :absolute
   :padding 0
   :display :block
   :color "#5D6071"

   :font-family "Roboto"
   :font-size "1.0rem"
   :font-weight 400
   :line-height 1
   :transform "translate(0, 1.5px) scale(0.75)"
   :transform-origin "top left"})

(defn user-role-style
  []
  {:white-space :nowrap
   :overflow :ellipsis
   :margin-top "16px"
   :padding "6px 24px 0 0"
   :line-height "19px"
   :position :relative
   :color theme-colors/blue})

(defn divider-style
  []
  ^{:pseudo {:last-child {:border :none}}}
  {:border-color theme-colors/gray-lighter
   :border-width "0 1px 0 0"
   :border-style "solid"
   :padding "0 1rem 0 1rem"})

(defn logo-style
  []
  {:margin-right "1rem"
   :display :flex
   :flex-direction :row
   :justify-content :flex-start
   :flex-grow 1
   :flex-basis "15%"})

(defn logout-container-style
  []
  {:border 0
   :margin 0
   :display :inline-flex
   :position :relative
   :flex-direction :column
   :vertical-align :top})

(defn logout-style
  []
  {:white-space :nowrap
   :padding "22px 0 0 0"
   :line-height "19px"
   :position :relative
   :font-size "16px"
   :color theme-colors/blue})
