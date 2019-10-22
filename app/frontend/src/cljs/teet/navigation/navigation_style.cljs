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
   :justify-content :space-between
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
