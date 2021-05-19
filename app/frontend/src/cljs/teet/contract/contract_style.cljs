(ns teet.contract.contract-style
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.common.responsivity-styles :as responsivity-styles]))

(defn contract-page-container
  []
  {:padding "2rem 2.5rem"
   :background-color theme-colors/white
   :flex 1})

(defn search-header
  []
  ^{:combinators {[:> :div :span] {:background :red}}}
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
