(ns teet.project.project-style
  (:require [teet.theme.theme-spacing :as theme-spacing]
            [teet.theme.theme-colors :as theme-colors]))

(defn project-grid-container []
  {})

(defn project-map-column []
  {:padding-top "0px!important"
   :padding-right "0px!important"
   :padding-bottom "0px!important"
   :position "fixed"
   :width "100%"
   :right "0px"
   :top theme-spacing/appbar-height})

(defn project-data-column []
  {})

(defn phase-list-style
  []
  {:margin-top "2rem"})

(defn project-view-container
  []
  {:display :flex})

(defn project-tasks-style
  []
  {:flex 1
   :padding-right "1rem"})

(defn project-map-style
  []
  {:flex 1
   :position "sticky"
   :top theme-spacing/appbar-height
   :align-self :flex-start})

(defn project-info-style
  []
  {:background-color theme-colors/light-gray
   :padding "2rem 1.5rem 0 1.5rem"})

(defn project-data-style
  []
  {:margin-bottom "1rem"})

(defn restriction-button-style
  []
  {:display :flex
   :width "100%"
   :justify-content :start
   :padding "1rem 0"})

(defn restriction-container
  []
  (merge
    {:border-width "1px 0"
     :border-style "solid"
     :border-color theme-colors/gray100
     :margin-top "-1px"}))

(defn restriction-list-style
  []
  {:padding "1rem"})

(defn restriction-category-style
  []
  {:padding "1.5rem 0"
   :text-transform "capitalize"})
