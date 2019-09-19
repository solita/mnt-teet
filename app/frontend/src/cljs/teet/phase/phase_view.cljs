(ns teet.phase.phase-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.phase.phase-controller :as phase-controller]
            [teet.localization :refer [tr]]))

(defn phase-form [e! phase]
  ;; Phase name (drop-down selector, a predefined list of phases: eskiisprojekt, eelprojekt, põhiprojekt, maade omandamine, ehitus)
  ;; Timeline (EstStart, EstEnd, assumptions entered only)
  ;; Status (drop-down selector, a predefined list of statuses)
  (let [update-field-fn (fn [field]
                          #(e! (phase-controller/->UpdatePhaseForm {field %})))]
    [:<>
     [select/select-enum {:e! e!
                          :attribute :phase/phase-name
                          :value (or (:phase/phase-name phase) :none)
                          :on-change (update-field-fn :phase/phase-name)}]


     [date-picker/date-input {:value (:phase/due-date phase)
                              :on-change (update-field-fn :phase/due-date)}]



     ]))
