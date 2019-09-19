(ns teet.phase.phase-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.phase.phase-controller :as phase-controller]
            [teet.localization :refer [tr]]))

(defn phase-form [e! phase]
  ;; Phase name (drop-down selector, a predefined list of phases: eskiisprojekt, eelprojekt, põhiprojekt, maade omandamine, ehitus)
  ;; Timeline (EstStart, EstEnd, assumptions entered only)
  ;; Status (drop-down selector, a predefined list of statuses)
  [:<>
   [select/outlined-select {:label (tr [:project :phase :name])
                            :items [:none
                                    :eskiisprojekt
                                    :eelproject
                                    :pohiprojekt
                                    :maade-omandamine
                                    :ehitus]
                            :format-item #(case %
                                            :none (tr [:common :select :empty])
                                            ;; phases need to be in database
                                            (name %))
                            :value (or (:phase/name phase) :none)
                            :on-change #(e! (phase-controller/->UpdatePhaseForm {:phase/name %}))}]


   [date-picker/date-input {:value (:phase/due-date phase)
                            :on-change #(e! (phase-controller/->UpdatePhaseForm {:phase/due-date %}))}]



   ])
