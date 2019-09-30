(ns teet.phase.phase-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.phase.phase-controller :as phase-controller]
            [teet.localization :refer [tr]]
            [teet.ui.material-ui :refer [Grid Button]]
            [teet.ui.panels :as panels]
            [teet.ui.form :as form]
            teet.document.document-spec))

(defn phase-form [e! close phase]
  ;; Phase name (drop-down selector, a predefined list of phases: eskiisprojekt, eelprojekt, põhiprojekt, maade omandamine, ehitus)
  ;; Timeline (EstStart, EstEnd, assumptions entered only)
  ;; Status (drop-down selector, a predefined list of statuses)
  [form/form {:e! e!
              :value phase
              :on-change-event phase-controller/->UpdatePhaseForm
              :save-event phase-controller/->CreatePhase
              :cancel-event close
              :spec :document/new-phase-form}
   ^{:attribute :phase/phase-name}
   [select/select-enum {:e! e! :attribute :phase/phase-name}]

   ^{:xs 6 :attribute :phase/estimated-start-date}
   [date-picker/date-input {}]

   ^{:xs 6 :attribute :phase/estimated-end-date}
   [date-picker/date-input {}]

   ^{:attribute :phase/status}
   [select/select-enum {:e! e! :attribute :phase/status}]])
