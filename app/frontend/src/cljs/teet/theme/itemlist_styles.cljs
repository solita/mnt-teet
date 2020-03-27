(ns teet.theme.itemlist-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn heading
  [_variant]
  {:margin-bottom   "1rem"
   :display         :flex
   :justify-content :space-between
   :align-items     :flex-end})

(defn heading-action []
  {:float :right})

(defn checkbox-list-contents
  []
  {:padding       "0.5rem 1rem"
   :margin-left   "2rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})

(defn checkbox-container
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}}}
  {:transition "background-color 0.2s ease-in-out"
   :width        "100%"})

(defn checkbox-label
  [selected?]
  {:font-weight (if selected?
                  "bold !important"
                  "normal")})

(defn checkbox-list-link []
  ;; Pad checkbox list link to make it align with checkbox item labels
  {:margin-right "5rem"})

(defn layer-checkbox
  []
  {:margin-right "0.5rem"})

(defn gray-bg-list-element
  []
  (let [border-style (str "1px solid " theme-colors/gray-lighter)]
    ^{:pseudo {:first-of-type {:border-top border-style}}}
    {:list-style       :none
     :border-bottom    border-style
     :padding          "0.5rem"
     :background-color theme-colors/gray-lightest}))

(defn white-link-style
  [selected?]
  ^{:pseudo {:hover {:text-decoration :underline}}}
  {:color theme-colors/white
   :text-decoration :none
   :font-weight (if selected?
                  :bold
                  :normal)})

(defn white-link-item-style
  []
  {:list-style :none})
