(ns teet.comments.comments-styles
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [defkeyframes]]))

(defkeyframes focus-animation
  ["0%" {:background-color theme-colors/gray}]
  ["75%" {:background-color theme-colors/gray-light}]
  ["100%" {:background-color "transparent"}])

(defn comment-entry [focused?]
  (merge (common-styles/margin-bottom 1)
         (when focused?
           {:animation [[focus-animation "1.5s"]]})))

(defn comment-buttons-style
  []
  {:background-color theme-colors/gray-lightest
   :justify-content  :flex-end
   :display          :flex
   :align-items      :center
   :padding          "0 1.5rem 1.5rem 1.5rem"})

(defn comment-amount
  []
  {:margin-left      "1rem"
   :height           "1.25rem"
   :width            "1.25rem"
   :font-size        "0.75rem"
   :font-weight      :bold
   :line-height      "1.125rem"
   :text-align       :center
   :color            theme-colors/white
   :background-color theme-colors/gray
   :border-radius    "50%"})

(defn attachment-list
  []
  {:background-color theme-colors/white
   :border (str "solid 1px " theme-colors/gray-light)
   :display :flex
   :flex-direction :column
   :align-items :stretch})

(defn attachment-list-item
  []
  ^{:pseudo {:last-child {:border-bottom 0}}}
  {:border-bottom (str "solid 1px" theme-colors/gray-lighter)
   :padding-top "0.5rem"
   :padding-bottom "0.5rem"
   :margin-left "0.5rem"
   :margin-right "0.5rem"
   :display :flex
   :align-items :flex-end
   :justify-content :space-between})

(defn attachment-link
  []
  ^{:pseudo {:hover {:text-decoration :none}}}
  {:color theme-colors/blue})

(defn data
  []
  {:color theme-colors/gray-light
   :font-size "0.75rem"
   :padding-left "0.5rem"})
