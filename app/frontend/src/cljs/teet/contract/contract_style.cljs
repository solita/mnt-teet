(ns teet.contract.contract-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn contract-page-container
  []
  {:padding "2rem"
   :background-color :white
   :flex 1})

(defn project-contract-container
  []
  ^{:pseudo {:last-of-type {:border-bottom 0}}}
  {:margin-bottom "1rem"
   :padding-bottom "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})
