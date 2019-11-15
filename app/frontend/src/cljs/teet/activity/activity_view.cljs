(ns teet.activity.activity-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.activity.activity-controller :as activity-controller]
            [teet.localization :refer [tr]]
            [teet.ui.form :as form]
            teet.document.document-spec))

(defn activity-form [e! close activity]
  ;; Activity name (drop-down selector, a predefined list of activities: eskiisprojekt, eelprojekt, põhiprojekt, maade omandamine, ehitus)
  ;; Timeline (EstStart, EstEnd, assumptions entered only)
  ;; Status (drop-down selector, a predefined list of statuses)
  [form/form {:e! e!
              :value activity
              :on-change-event activity-controller/->UpdateActivityForm
              :save-event activity-controller/->CreateActivity
              :cancel-event close
              :spec :document/new-activity-form}
   ^{:attribute :activity/name}
   [select/select-enum {:e! e! :attribute :activity/name}]

   ^{:attribute :activity/estimated-date-range}
   [date-picker/date-range-input {:start-label (tr [:fields :activity/estimated-start-date]) :end-label (tr [:fields :activity/estimated-end-date])}]

   ^{:attribute :activity/status}
   [select/select-enum {:e! e! :attribute :activity/status}]])
