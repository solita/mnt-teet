(ns teet.contract.contract-style
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.common.responsivity-styles :as responsivity-styles]))

(defn contract-page-container
  []
  {:padding "2rem 2.5rem"
   :background-color theme-colors/white
   :flex 1})
(defn contract-search-container
  []
  {:display :flex
   :flex-direction :column})
(defn search-header
  []
  {:border-bottom (str "1px solid " theme-colors/border-dark)})

(defn quick-filters-header
  []
  {:font-size "0.75rem"
   :color theme-colors/text-medium-emphasis
   :margin-top "1.56rem"})

(defn search-shortcuts
  []
  {:margin-top "1rem"
   :display :flex
   :justify-content :space-between})

(defn search-shortcut-items
  []
  {:flex 2})

(defn search-shortcut-item
  [selected?]
  {:font-size "0.875rem"
   :margin-right "1.875rem"
   :color theme-colors/text-medium-emphasis
   :font-weight (if selected?
             "bold"
             "normal")})

(defn filter-inputs
  []
  {:margin-top "2.625rem"
   :display :grid
   :grid-template-columns "repeat(4, 1fr)"
   :column-gap "1rem"
   :row-gap "1rem"})

(defn clear-filters-button
  []
  ^{:combinators
    {[:> :span] {:font-size "1rem"
                 :margin-right "0.34rem"}}}
  {:float :right
   :color theme-colors/text-medium-emphasis
   :margin-top "1.31rem"
   :align-self :flex-end})

(defn filter-input
  []
  {:width "25%"})

(defn project-contract-container
  []
  ^{:pseudo {:last-of-type {:border-bottom 0}}}
  {:margin-bottom "1rem"
   :padding-bottom "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})
