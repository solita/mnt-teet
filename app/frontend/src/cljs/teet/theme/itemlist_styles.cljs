(ns teet.theme.itemlist-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn heading
  [variant]
  {:margin-bottom   "1rem"
   :display         :flex
   :justify-content :space-between
   :align-items     :flex-end})

(defn heading-action []
  {:float :right})

(defn checkbox-list-contents
  []
  {:padding       "0.5rem 1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})

(defn checkbox-container
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}}}
  {:transition "background-color 0.2s ease-in-out"
   :padding-left "45px"
   :width        "100%"})

(defn checkbox-label
  [selected?]
  {:font-weight (if selected?
                  "bold !important"
                  "normal")})

(defn layer-checkbox
  []
  {:margin-right "0.5rem"})
