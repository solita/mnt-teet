(ns teet.comments.comments-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

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
  {:background-color theme-colors/gray-lightest
   :border (str "solid 1px " theme-colors/gray-dark)})

(defn attachment-list-item
  []
  {:display :inline-block
   :margin "0.5rem"})

(defn attachment-list-separator
  []
  {:width "95%"})

(defn edited
  []
  {:color theme-colors/gray-light
   :font-size "0.75rem"
   :padding-left "0.5rem"})
