(ns teet.activity.activity-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.localization :refer [tr]]
            [teet.ui.form :as form]
            teet.document.document-spec
            [teet.project.project-navigator-view :as project-navigator-view]))

(defn activity-form [e! {:keys [close activity save on-change delete lifecycle-type]}]
  ;; Activity name (drop-down selector, a predefined list of activities: eskiisprojekt, eelprojekt, põhiprojekt, maade omandamine, ehitus)
  ;; Timeline (EstStart, EstEnd, assumptions entered only)
  ;; Status (drop-down selector, a predefined list of statuses)
  [form/form {:e! e!
              :value activity
              :on-change-event on-change
              :save-event save
              :cancel-event close
              :delete delete
              :spec :document/new-activity-form}
   ^{:attribute :activity/name}
   [select/select-enum {:e! e! :attribute :activity/name :enum/valid-for lifecycle-type}]

   ^{:attribute :activity/estimated-date-range}
   [date-picker/date-range-input {:start-label (tr [:fields :activity/estimated-start-date]) :end-label (tr [:fields :activity/estimated-end-date])}]

   ^{:attribute :activity/status}
   [select/select-enum {:e! e! :attribute :activity/status}]])

(defn activity-page [e! app project breadcrumbs]
  [project-navigator-view/project-navigator-with-content
   e! project app

   [:div "tässäpä activity"]])
