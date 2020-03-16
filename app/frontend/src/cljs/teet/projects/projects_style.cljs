(ns teet.projects.projects-style
  (:require [teet.theme.theme-colors :as theme-colors]))


(defn status-circle-style
  [status]
  {:height "15px"
   :width "15px"
   :border-radius "100%"
   :flex-shrink 0
   :margin-right "1rem"
   :background-color (case status
                       :task-over-deadline
                       theme-colors/yellow
                       :on-schedule
                       theme-colors/green
                       :unassigned-over-start-date
                       theme-colors/orange-light
                       :activity-over-deadline
                       theme-colors/orange-light
                       theme-colors/gray-lighter)})

(defn name-and-status-row-style
  []
  {:display :flex
   :align-items :center})


(defn project-name-style
  []
  {:display :block})
