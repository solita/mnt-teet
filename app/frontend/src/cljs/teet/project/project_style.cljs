(ns teet.project.project-style
  (:require [teet.theme.theme-spacing :as theme-spacing]))

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
   :margin-right "-24px"                                    ;;This is done to off-set the margin given in body. Should probably be done differently
   :padding-left "1rem"
   :position "sticky"
   :top theme-spacing/appbar-height
   :align-self :flex-start})
