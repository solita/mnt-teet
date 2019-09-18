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
