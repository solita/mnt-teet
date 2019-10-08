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

(defn section-spacing
  []
  {:padding "2rem 1.5rem 0 1.5rem"})


(defn project-data-style
  []
  {:margin-bottom "1rem"})
