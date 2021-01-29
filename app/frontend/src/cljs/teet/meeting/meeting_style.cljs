(ns teet.meeting.meeting-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn meeting-container-heading-box
  []
  {:width "100%"
   :display :flex
   :justify-content :space-between
   :align-items :center})

(defn meeting-container-heading
  []
  {:border-bottom (str "1px solid " theme-colors/gray-lighter)
   :padding-bottom "1rem"
   :padding-top "1rem"})
