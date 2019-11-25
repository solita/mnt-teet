(ns teet.project.project-style
  (:require [teet.theme.theme-spacing :as theme-spacing]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-styles :as common-styles]))

(defn project-map-column []
  {:padding-top "0px!important"
   :padding-right "0px!important"
   :padding-bottom "0px!important"
   :position "fixed"
   :width "100%"
   :right "0px"
   :top theme-spacing/appbar-height})


(defn project-view-container
  []
  {:display :flex})

(defn project-map-style
  []
  {:flex 1
   :position "sticky"
   :top theme-spacing/appbar-height
   :align-self :flex-start})

(defn project-info
  []
  {:padding "1.5rem 0"})

(defn restriction-button-style
  []
  {:display :flex
   :width "100%"
   :justify-content :space-between
   :padding "1rem 0"})

(defn restriction-container
  []
  {:border-width "0 0 1px 0"
   :border-style "solid"
   :border-color theme-colors/gray-lighter})

(defn activity-action-heading
  []
  {:display :flex
   :justify-content :space-between
   :align-items :center
   :padding-bottom "0.75rem"
   :border-bottom (str "1px solid " theme-colors/gray-light)})

(defn restriction-list-style
  []
  {:padding "1rem"})

(defn restriction-category-style
  []
  {:padding "1.5rem 0"
   :text-transform "capitalize"
   :border-bottom (str "1px solid " theme-colors/gray-light)})

(defn heading-state-style
  []
  {:display :flex
   :justify-content :space-between
   :align-items :center})

(defn project-activity-style
  []
  ^{:pseudo {:last-child {:border-bottom 0}}}
  {:border-bottom (str "1px solid " theme-colors/gray-light)})

(defn top-margin
  []
  {:margin-top "1rem"})

(defn link-button-style
  []
  {:margin "1rem 0"})

(defn project-content-overlay []
  (merge
   (common-styles/content-paper-style)
   {:position "absolute"
    :left "25px"
    :top "25px"
    :bottom "25px"
    :width "30vw"
    :max-height "calc(100vh - 275px)"
    :overflow-y :auto
    :display :flex
    :flex-direction :column}))

(defn content-overlay-inner []
  {:padding 0
   :flex 1
   :display :flex
   :flex-direction :column})

(defn initialization-form-wrapper
  []
  {:flex 1
   :display :flex
   :flex-direction :column
   :justify-content :space-between})

(defn initialization-form
  []
  {:display :flex
   :flex-direction :column
   :justify-content :space-between})

(defn wizard-header
  []
  {:padding "0.5rem"
   :background-color theme-colors/gray-lightest
   :border-bottom (str "1px solid" theme-colors/gray-lighter)})

(defn wizard-header-step-info
  []
  {:display :flex
   :flex-direction :row
   :justify-content :space-between})

(defn wizard-form
  []
  {:background-color theme-colors/white
   :padding "1.5rem"})

(defn wizard-footer
  []
  {:display :flex
   :flex-direction :row
   :justify-content :space-between
   :padding "0.5rem"
   :background-color theme-colors/gray-lightest
   :border-top (str "1px solid" theme-colors/gray-lighter)
   :align-items :center})
