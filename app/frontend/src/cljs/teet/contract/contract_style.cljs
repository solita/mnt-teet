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
  {:padding "0.8rem"
   :box-shadow theme-colors/card-box-shadow
   :background-color theme-colors/card-background-extra-light
   :margin-top "1rem"})

(defn contract-card-style-container
  []
  {:padding "0"})

(defn contract-card-style-header
  []
  ^{:combinators
    {[:> :h3] {:display :none}
     [:> :span :button] {:margin-right "0.3rem"}}}
  {:display :flex})

(defn contract-card-header-component-style
  []
  ^{:combinators
    {[:> :h4] {:font-size "1rem"
               :font-weight "700"
               :text-transform :uppercase
               :margin "0 0.8rem"
               :flex 1}}}
  {:display :flex
   :flex 1})

(defn contract-card-header-component-status-style
  []
  {:height "1.75rem"
   :display :flex})

(defn contract-card-details-style
  []
  ^{:combinators {[:descendant :pseudo :first-child] {:margin-bottom "1.5rem"
                            :background-color :red}}}
  {:margin-left "2.175rem"
   :margin-top "1.75rem"
   :display :grid
   :grid-template-columns "repeat(1, 1fr)"
   :grid-template-rows "repeat(2, auto)"
   :gap "1.5rem"})

(defn contracts-list-style
  []
  {:margin-top "3.857rem"})

(defn contracts-list-header-style
  []
  {:display :flex
   :justify-content :space-between
   :margin-bottom "2.1rem"})

(defn contract-list-secondary-button
  [margin-right]
  {:padding "0 1rem"
   :box-sizing :border-box
   :height "1.5rem"
   :margin-right (if margin-right margin-right 0)})

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

(defn partners-page-container
  []
  {:background-color theme-colors/white
   :flex 1
   :display :flex})

(defn project-contract-container-style
  []
  ^{:pseudo {:last-of-type {:border-bottom 0}}}
  {:margin-bottom "1rem"
   :padding-bottom "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})


(defn contract-partners-panel-style
  []
  {:min-width "400px"
   :border-right "1px solid black"})
