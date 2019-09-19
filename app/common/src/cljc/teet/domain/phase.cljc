(ns teet.domain.phase
  "Phase domain entity logic")

;; Note: these should be queried from db?

(def ^{:doc "Selectable pre-defined phases for project"}
  phase-names
  [:phase.name/pre-design
   :phase.name/preliminary-design
   :phase.name/detailed-design
   :phase.name/land-acquisition
   :phase.name/construction
   :phase.name/other])

(def ^{:doc "Selectable pre-defined phase statuses"}
  phase-statuses
  [:phase.status/in-progress
   :phase.status/research
   :phase.status/completed
   :phase.status/valid
   :phase.status/canceled
   :phase.status/expired
   :phase.status/other])
