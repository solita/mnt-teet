(ns teet.link.link-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn link-row-style
  [iseditable?]
  (merge {:flex :auto
          :padding "0.5rem 0.5rem 0.5rem 0"
          :min-width 0}
         (when iseditable?
           {:border-right (str "solid " theme-colors/gray-lighter " 2px")})))

(defn link-row-editable-box
  []
  {:flex 0
   :align-self :center
   :padding-left "0.5rem"})

(defn link-row
  []
  {:display :flex
   :flex-direction :column
   :justify-content :center})

(defn link-row-heading-line
  []
  {:display :flex
   :align-items :center})

(defn link-row-icon
  []
  {:padding-right "0.5rem"})
