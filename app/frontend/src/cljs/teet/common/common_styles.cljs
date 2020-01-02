(ns teet.common.common-styles
  (:require [teet.theme.theme-colors :as theme-colors]
            [herb.core :refer [defglobal]]))

(defn gray-light-border
  []
  {:display        :flex
   :align-items    :center
   :padding-bottom "0.75rem"
   :margin-bottom  "1rem"
   :border-bottom  (str "1px solid " theme-colors/gray-light)})

(defn top-info-spacing
  []
  {:padding "1.5rem 1.875rem"})

(defn spinner-style
  []
  {:flex            1
   :align-items     :center
   :display         :flex
   :justify-content :center})

(defn input-error-text-style
  []
  {:font-size "1rem"
   :color     theme-colors/error
   :position  :absolute})

(defn grid-left-item
  []
  {:display        :flex
   :flex-direction :column})

(defn tab-link [current?]
  (let [color (if current? theme-colors/blue-dark theme-colors/blue-light)]
    ^{:pseudo {:hover {:color theme-colors/blue-dark
                       :fill theme-colors/blue-dark}}}
    {:font-weight (if current? "bold" "normal")
     :display     "inline-block"
     :color       color
     :fill        color
     :text-align  "center"}))

(defn tab-icon [current?]
  {:width  "40px"
   :height "35px"
   :margin "1px 5px 1px 5px"})

(defn gray-text []
  {:color theme-colors/gray-light})

(defn inline-block []
  {:display "inline-block"})

(defn list-item-link []
  (let [border (str "solid 1px " theme-colors/gray-lighter)]
    ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
               :last-child {:border-bottom border}}}
    {:border-top border
     :padding "1rem"
     :display "flex"
     :color theme-colors/primary-text
     :text-decoration "none"
     :transition "background-color 0.2s ease-in-out"}))

(defn content-paper-style
  []
  {:border-radius "3px"
   :border (str "1px solid " theme-colors/gray-lighter)
   :box-shadow "none"})

(defglobal global
           [:body :html {:height "100%"}]
           [:#teet-frontend {:height "100%"}]
           [:input :select :textarea :button {:font-family :inherit}])
