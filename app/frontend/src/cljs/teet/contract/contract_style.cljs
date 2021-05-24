(ns teet.contract.contract-style
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.common.responsivity-styles :as responsivity-styles]))

(defn contract-page-container-style
  []
  {:padding "2rem 2.5rem"
   :background-color theme-colors/white
   :flex 1})

(defn contract-card-style
  []
  {})
(defn contracts-list-style
  []
  {:margin-top "3.857rem"})

(defn contracts-list-header-style
  []
  {:display :flex
   :justify-content :space-between})

(defn toggle-list-expansion-button-style
  []
  ^{:combinators
    {[:> :span] {:color theme-colors/primary
                 :margin-left "0.25rem"}}}
  {:font-size "0.875rem"
   :display :none ; TODO temporarily hidden until the functionality is implemented
   :flex-direction :row-reverse
   :color theme-colors/text-medium-emphasis
   :text-decoration :none})

(defn contract-search-container-style
  []
  {:display :flex
   :flex-direction :column})

(defn search-header-style
  []
  {:border-bottom (str "1px solid " theme-colors/border-dark)})

(defn quick-filters-header-style
  []
  {:font-size "0.75rem"
   :color theme-colors/text-medium-emphasis
   :margin-top "1.56rem"})

(defn search-shortcuts-style
  []
  {:margin-top "1rem"
   :display :flex
   :justify-content :space-between})

(defn search-shortcut-items-style
  []
  {:flex 2})

(defn search-shortcut-item-style
  [selected?]
  {:font-size "0.875rem"
   :margin-right "1.875rem"
   :color theme-colors/text-medium-emphasis
   :font-weight (if selected?
             "bold"
             "normal")})

(defn filter-inputs-style
  []
  {:margin-top "2.625rem"
   :display :grid
   :grid-template-columns "repeat(5, 1fr)"
   :column-gap "1rem"
   :row-gap "1rem"})

(defn clear-filters-button-style
  []
  ^{:combinators
    {[:> :span] {:font-size "1rem"
                 :margin-right "0.34rem"}}}
  {:float :right
   :color theme-colors/text-medium-emphasis
   :margin-top "1.31rem"
   :align-self :flex-end})

(defn filter-input-style
  []
  {:width "20%"})

(defn project-contract-container-style
  []
  ^{:pseudo {:last-of-type {:border-bottom 0}}}
  {:margin-bottom "1rem"
   :padding-bottom "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})
