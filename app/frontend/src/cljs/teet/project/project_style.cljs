(ns teet.project.project-style
  (:require [teet.theme.theme-spacing :as theme-spacing]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-styles :as common-styles]
            [teet.common.responsivity-styles :as responsivity-styles]))

(defn project-map-column []
  {:padding-top "0px!important"
   :padding-right "0px!important"
   :padding-bottom "0px!important"
   :position "fixed"
   :width "100%"
   :right "0px"
   :top theme-spacing/appbar-height})

(defn project-map-container
  []
  {:position "relative"
   :display "flex"
   :flex-direction "column"
   :height "calc(100vh - 220px)"}                           ;;HTML structure needs refactor so these cals wouldn't be needed
  )

(defn project-page-structure
  []
  {:display        :flex
   :flex-direction :column
   :flex           1
   :max-height     theme-spacing/content-height
   :overflow       :hidden})

(defn permission-container
  []
  {:background-color theme-colors/gray-lightest
   :padding "1rem"
   :margin-bottom "2rem"})

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

(defn project-panel-width []
  (int (* 0.35 js/window.innerWidth)))

(defn project-content-overlay []
  (merge
   (common-styles/content-paper-style)
   {:position "absolute"
    :left "25px"
    :top "25px"
    :bottom "25px"
    :width (str (project-panel-width) "px")
    :min-width "470px"
    :max-width "500px"
    :display :flex
    :flex-direction :column}))

(defn content-overlay-inner []
  {:padding "1rem"
   :flex 1
   :display :flex
   :flex-direction :column
   :overflow-y :scroll})

(defn initialization-form-wrapper
  []
  {:flex 1
   :display :flex
   :flex-direction :column
   :justify-content :space-between})

(defn project-view-header
  []
  {:padding "0.75rem 0.5rem"
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
   :padding "0.5rem"})

(defn wizard-footer
  []
  {:display :flex
   :flex-direction :row
   :justify-content :space-between
   :padding "1rem"
   :background-color theme-colors/gray-lightest
   :border-top (str "1px solid" theme-colors/gray-lighter)
   :align-items :center})

(defn project-timeline-link
  [dark-theme?]
  ^{:pseudo {:hover {:color theme-colors/blue-dark}}}
  (merge
   {:margin-left "1rem"
    :text-decoration :none}
   (when dark-theme?
     {:color theme-colors/white})))

(defn page-container []
  {:display        :flex
   :flex-direction :column
   :flex           1})

(defn project-view-selection-menu []
  {:margin-top "2.5rem"})

(defn project-view-selection-item []
  ^{:pseudo {:hover {:text-decoration :none}}}
  {:min-width "250px"
   :color theme-colors/gray-dark})

(defn project-view-selection-item-label []
  {:width "200px"})

(defn project-view-selection-item-hotkey []
  {:background-color theme-colors/gray-lightest
   :font-size "75%"
   :padding "0px 5px 0px 5px"
   :border-radius "4px"})

(defn project-tab-container [dark-theme?]
  (merge
    {:padding "1rem"}
    (when dark-theme?
      {:background-color theme-colors/gray-dark
       :color :white})))

(defn owner-container []
  {:padding "1rem"
   :padding-bottom "0.5rem"
   :margin-bottom "0.5rem"
   :margin-right "1rem"
   :background-color theme-colors/gray-lightest})

(defn owner-info []
  {:display :flex
   :flex-direction :column
   :justify-content :flex-top})

(defn owner-comments-container []
  {:margin-bottom "0.5rem"
   :margin-right "1rem"})

(defn owner-heading-container []
  {:margin-bottom "0.5rem"})

(defn owner-info-header []
  {:display :flex
   :flex-direction :row
   :justify-content :space-between})

(defn owner-info-name-and-code []
  {:margin-bottom "0.5rem"})

(defn owner-info-registry-info []
  {:padding-left "0.5rem"
   :margin-bottom "0.5rem"
   :border-left (str "solid 7px " theme-colors/gray-light)})

(defn desktop-scroll-content-separately []
  (responsivity-styles/desktop-only-style {:overflow-y :auto}))
