(ns teet.common.common-styles
  (:require [teet.theme.theme-colors :as theme-colors]
            [herb.core :refer [defglobal]]
            [garden.color :refer [darken]]))

(defn gray-light-border
  []
  {:display        :flex
   :align-items    :center
   :padding-bottom "0.75rem"
   :margin-bottom  "0.5rem"
   :border-bottom  (str "1px solid " theme-colors/gray-light)})

(defn top-info-spacing
  []
  {:padding "1.5rem 1.875rem"})

(defn spinner-style
  []
  {:flex            1
   :align-items     :center
   :display         :flex
   :justify-content :center})

(defn grid-left-item
  []
  {:display        :flex
   :flex-direction :column})

(defn tab-link [current?]
  (let [color (if current? theme-colors/blue-dark theme-colors/blue-light)]
    ^{:pseudo {:hover {:color theme-colors/blue-dark
                       :fill theme-colors/blue-dark}}}
    {:font-weight (if current? "bold" "normal")
     :display     "inline-block"
     :color       color
     :fill        color
     :text-align  "center"}))

(defn tab-icon [_current?]
  {:width  "40px"
   :height "35px"
   :margin "1px 5px 1px 5px"})

(defn gray-text []
  {:color theme-colors/gray-light})


(defn warning-text []
  {:color theme-colors/error
   :font-size "1.125rem"})

(defn green-text []
  {:color theme-colors/green})

(defn inline-block []
  {:display "inline-block"})

(defn list-item-link []
  (let [border (str "solid 1px " theme-colors/gray-lighter)]
    ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
               :last-child {:border-bottom border}}}
    {:border-top border
     :padding "1rem"
     :display "flex"
     :color theme-colors/primary-text
     :text-decoration "none"
     :transition "background-color 0.2s ease-in-out"}))

(defn content-paper-style
  []
  {:border-radius "3px"
   :border (str "1px solid " theme-colors/gray-lighter)
   :box-shadow "none"})

(defglobal global
           [:body :html {:height "100%"}]
           [:p {:margin 0}]
           ;; Richtexteditor styling
           [:h1 {:margin-top 0}]
           ;;
           [:#teet-frontend {:height "100%"}]
           [:.mention
            [:textarea {:border :none}]
            [:.comment-textarea__control {:background-color theme-colors/white
                                          :border (str "1px solid " theme-colors/gray-light)}]]
           [:.comment-textarea__highlighter {:padding "10px"
                                             :border "1px solid transparent"}]
           [:.comment-textarea__input {:padding "10px"}]
           [:.comment-textarea__suggestions__list {:background-color theme-colors/white
                                                   :border (str "1px solid " theme-colors/gray-light)
                                                   :font-size "14px"
                                                   :overflow :auto}]
           [:.comment-textarea__suggestions__item {:padding "5px 15px"
                                                    :border-bottom (str "1px solid " theme-colors/gray-light)}]
           [:.comment-textarea__suggestions__item--focused {:background-color theme-colors/blue-lightest}]
           [:input :select :textarea :button {:font-family :inherit}]
           ["input::-webkit-outer-spin-button" "input::-webkit-inner-spin-button" {"-webkit-appearance" "none" :margin 0}])

(defn header-with-actions []
  {:margin-top "2rem"
   :justify-content :space-between
   :display :flex})

(defn space-between-center
  []
  {:display :flex
   :justify-content :space-between
   :align-items :center})

(defn input-error-text-style
  []
  {:font-size "1rem"
   :color theme-colors/error
   :text-align :center})

(defn white-space-nowrap
  []
  {:white-space :nowrap})

(defn input-start-text-adornment
  "Style for a short text adornment before input text"
  []
  {:padding "0px 7px 0px 7px"
   :left "1px"
   :top "1px"
   :min-height "39px"
   :background-color theme-colors/gray-lighter
   :color theme-colors/gray-dark
   :user-select :none})

(defn flex-row-space-between
  []
  {:display :flex
   :flex-direction :row
   :justify-content :space-between})

(defn margin-bottom
  [rem]
  {:margin-bottom (str rem "rem")})

(defn margin-left
  [rem]
  {:margin-left (str rem "rem")})

(defn flex-row
  []
  {:display :flex
   :flex-direction :row})

(defn flex-row-end
  []
  {:display :flex
   :flex-direction :row
   :align-items :flex-end})

(defn margin-right
  [rem]
  {:margin-right (str rem "rem")})

(defn flex-row-wrap
  []
  {:display :flex
   :flex-direction :row
   :flex-wrap :wrap})

(defn flex-table-column-style
  "Style for column in 'tables' that are made with flex blocks"
  ([basis]
   (flex-table-column-style basis :flex-start))
  ([basis justify-content]
   (flex-table-column-style basis justify-content 0))
  ([basis justify-content grow]
   ^{:pseudo {:first-child {:border-left 0}
              :last-child {:border-right 0}}}
   {:flex-basis (str basis "%")
    :border-color (darken theme-colors/gray-lighter 10)
    :border-style :solid
    :border-width "2px 2px 0 0"
    :flex-grow grow
    :flex-shrink 0
    :display :flex
    :align-items :center
    :padding "0.5rem 0.25rem"
    :justify-content justify-content}))

(defn heading-and-action-style
  []
  {:display         :flex
   :justify-content :space-between
   :margin-bottom   "1rem"
   :align-items :center})

(defn flex-align-center
  []
  {:display :flex
   :align-items :center})

(defn flex-align-end
  []
  {:display :flex
   :align-items :end})

(defn status-circle-style
  [color]
  {:height "15px"
   :font-size :inherit
   :width "15px"
   :border-radius "100%"
   :flex-shrink 0
   :margin-right "1rem"
   :background-color color})

(defn no-border
  []
  {:border :none})

(defn flex-row-w100-space-between-center
  []
  {:display :flex
   :flex-direction :row
   :width "100%"
   :align-items :center
   :justify-content :space-between})

(defn white-link-style
  [selected?]
  ^{:pseudo {:hover {:text-decoration :underline}}}
  {:color theme-colors/white
   :text-decoration :none
   :font-weight (if selected?
                  :bold
                  :normal)})

(defn gray-lightest-background-style []
  {:background-color theme-colors/gray-lightest})

(defn gray-container-style
  []
  {:background-color theme-colors/gray-lightest
   :padding "1.5rem"})

(defn label-text-style
  []
  {:display :block
   :font-size "1rem"})

(defn padding-bottom
  [amount]
  {:padding-bottom (str amount "rem")})

(defn padding
  "Add padding. Amounts specified in rem unit."
  ([vertical horizontal]
   (padding vertical horizontal vertical horizontal))
  ([up right down left]
   {:padding-up (str up "rem")
    :padding-right (str right "rem")
    :padding-down (str down "rem")
    :padding-left (str left "rem")}))

(defn no-margin []
  {:margin "0px"})

(defn flex-space-between-wrap
  []
  {:display :flex
   :justify-content :space-between
   :flex-wrap :wrap})

(defn divider-border
  "Top border to divide multiple items"
  []
  {:border-top (str "solid 1px " theme-colors/gray-light)})

(defn info-box [variant]
  (let [[border background] (case variant
                              :error [theme-colors/red theme-colors/red-lightest]
                              :info [theme-colors/blue theme-colors/blue-lightest])]
    {:border (str "solid 2px " border)
     :border-radius "3px"
     :background-color background
     :padding "1rem"
     :display :flex
     :flex-direction :column}))
