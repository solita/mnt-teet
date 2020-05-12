(ns teet.projects.projects-style
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-styles :as common-styles]))

(defn project-status-circle-style
  [status]
  (common-styles/status-circle-style (case status
                                       :task-over-deadline
                                       theme-colors/yellow
                                       :on-schedule
                                       theme-colors/green
                                       :unassigned-over-start-date
                                       theme-colors/orange-light
                                       :activity-over-deadline
                                       theme-colors/orange-light
                                       theme-colors/gray-lighter)))

(defn project-name-style
  []
  {:display :block})

(defn actitivies-ul-style
  []
  {:margin 0
   :padding-left "18px"})

(defn projects-table-container
  []
  {:padding "1.5rem"
   :overflow-x :scroll
   :min-width "1800px"})
