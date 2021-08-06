(ns teet.contract.contract-style
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.common.responsivity-styles :as responsivity-styles]))

(defn contract-page-container-style
  []
  {:padding "2rem 2.5rem"
   :background-color theme-colors/white
   :flex 1})

(defn contract-responsibilities-container-style
  []
  {:padding "1rem 1rem"
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

(defn filters-header-style
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

(defn responsibilities-page-container
  []
  {:background-color theme-colors/white
   :padding "0 1rem 0 1rem"})

(defn partner-info-header
  []
  ^{:combinators
    {[:> :h1] {:margin-right "1rem"
               :line-height "3rem"
               :font-size "2rem"}
     [:> :a] {:margin-left :auto}}}
  {:display :flex
   :align-items :center
   :margin-bottom "2rem"})

(defn personnel-section-style
  []
  {:display :flex
   :flex-direction :column})

(defn key-person-assignment-header
  []
  {:display :flex
   :align-items :baseline
   :justify-content :space-between})

(defn personnel-section-header-style
  []
  ^{:combinators
    {[:> :h2] {:margin-right "1rem"
               :line-height "2.6rem"
               :font-size "1.75rem"
               :font-weight "400"}
     [:> :a] {:margin-left :auto}}}
  {:display :flex
   :align-items :center
   :width "100%"
   :margin-top "1.562rem"})

(defn personnel-files-section-style
  []
  {:display :flex
   :align-items :baseline
   :justify-content :space-between})

(defn personnel-files-section-header-style
  []
  ^{:combinators
    {[:> :h2] {:margin-right "1rem"
               :line-height "2.6rem"
               :font-size "1.75rem"
               :font-weight "400"}
     [:> :a] {:margin-left :auto}}}
  {:display :flex
   :align-items :center
   :margin-top "1.562rem"})

(defn personnel-files-column-style
  []
  {:display :flex
   :flex-direction :column})

(defn personnel-table-style
  []
  ^{:combinators
    {[:> :h4] {:margin-bottom "1.5rem"}}}
  {:margin-top "2.5rem"})

(defn personnel-activation-link-style
  [active?]
  {:color (if active?
            theme-colors/red
            theme-colors/green)})

(defn project-contract-container-style
  []
  ^{:pseudo {:last-of-type {:border-bottom 0}}}
  {:margin-bottom "1rem"
   :padding-bottom "1rem"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})


(defn contract-partners-panel-style
  []
  {:min-width "15.6rem"
   :border-right (str "1px solid " theme-colors/border-dark)})

(defn responsibilities-table-row-style
  []
  ^{:pseudo {:first-of-type {:border-top :none
                             :border-right :none
                             :border-left :none}}}
  {:border-width "1px"
   :border-style :solid
   :border-color theme-colors/gray-lighter})

(defn responsibilities-table-heading-cell-style
  []
  {:white-space :nowrap
   :font-weight 500
   :font-size "0.875rem"
   :color theme-colors/gray
   :padding "1rem 0.5rem 1rem 0.5rem"
   :text-align :left})

(defn responsibilities-table-cell-style
  []
  {:padding "1rem 0.5rem 1rem 0.5rem"
   :border-width "1px"
   :border-style :solid
   :border-color theme-colors/gray-lighter})

(defn responsibilities-table-header-style []
  {:text-align :left
   :padding "1rem 0.5rem 1rem 0.5rem"
   :border-width "0 0 1px 0"
   :border-style :solid
   :border-color theme-colors/gray-lighter})

