(ns teet.navigation.navigation-style
  (:require [teet.theme.theme-spacing :as theme-spacing]
            [teet.theme.theme-colors :as theme-colors]))

(def drawer-width {true 200   ; width when open
                   false 80}) ; width when closed

(defn drawer
  [open?]
  (let [w (drawer-width open?)]
    {:min-width (str w "px")
     :width (str w "px")}))

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
   :box-shadow "0px 2px 4px rgba(0, 0, 0, 0.36)"})

(defn appbar-position [drawer-open?]
  (let [dw (drawer-width drawer-open?)]
    {:z-index 10
     :height "90px"
     :width (str "calc(100% - " dw "px)")
     :margin-left (str dw "px")}))

(defn main-container [drawer-open?]
  (let [dw (drawer-width drawer-open?)]
    {:z-index 10
     ;; :padding "0 24px"
     :width (str "calc(100% - " dw "px)")
     :margin-left (str dw "px")}))

(defn drawer-projects-style
  []
  {:text-transform :uppercase})

(defn drawer-list-item-style
  []
  ^{:pseudo {:first-child {:border-width "1px" }}}
  {:border-color theme-colors/blue-dark
   :border-width "0 1px 1px 1px"
   :border-style "solid"
   :padding "1rem"
   :display :flex
   :flex-direction :column
   :align-items :center})

(defn drawer-link-style
  [selected?]
  {:color (if selected?
            theme-colors/white
            theme-colors/gray-light)})
