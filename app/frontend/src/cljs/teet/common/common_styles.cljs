(ns teet.common.common-styles
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn gray-light-border
  []
  {:display :flex
   :align-items :center
   :padding-bottom "0.75rem"
   :margin-bottom "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-light)})

(defn gray-bg-content
  []
  {:background-color theme-colors/gray-lightest
   :padding "0.5rem 1.5rem 0 1.5rem"})
