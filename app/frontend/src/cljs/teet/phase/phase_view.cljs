(ns teet.phase.phase-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.phase.phase-controller :as phase-controller]
            [teet.localization :refer [tr]]
            [teet.ui.material-ui :refer [Grid Button]]
            [teet.ui.panels :as panels]))

(defn phase-form [e! close phase]
  ;; Phase name (drop-down selector, a predefined list of phases: eskiisprojekt, eelprojekt, põhiprojekt, maade omandamine, ehitus)
  ;; Timeline (EstStart, EstEnd, assumptions entered only)
  ;; Status (drop-down selector, a predefined list of statuses)
  (let [update-field-fn (fn [field]
                          #(e! (phase-controller/->UpdatePhaseForm {field %})))]
    [Grid {:container true :spacing 1}
     [Grid {:item true :xs 12}
      [select/select-enum {:e! e!
                           :id :phase/name
                           :name (tr [:fields :phase/name])
                           :attribute :phase/phase-name
                           :value (or (:phase/phase-name phase) :none)
                           :on-change (update-field-fn :phase/phase-name)}]]

     [Grid {:item true :xs 6}
      [date-picker/date-input {:label (tr [:fields :phase :estimated-start-date])
                               :value (:phase/estimated-start-date phase)
                               :on-change (update-field-fn :phase/estimated-start-date)}]]

     [Grid {:item true :xs 6}
      [date-picker/date-input {:label (tr [:fields :phase :estimated-end-date])
                               :value (:phase/estimated-end-date phase)
                               :on-change (update-field-fn :phase/estimated-end-date)}]]

     [Grid {:item true :xs 12}
      [select/select-enum {:e! e!
                           :id :phase/status
                           :name (tr [:fields :phase/name])
                           :attribute :phase/status
                           :value (:phase/status phase)
                           :on-change (update-field-fn :phase/status)}]]

     [Grid {:item true :xs 12 :align "right"}
      [Button {:on-click close
               :color "secondary"
               :variant "outlined"}
       (tr [:buttons :cancel])]
      [Button {:on-click #(e! (phase-controller/->CreatePhase))
               :color "primary"
               :variant "outlined"}
       (tr [:buttons :save])]]]))
