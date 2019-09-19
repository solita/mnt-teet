(ns teet.domain.phase
  "Phase domain entity logic")

(def ^{:doc "Selectable pre-defined phases for project"}
  phase-names
  [:pre-design
   :preliminary-design
   :detailed-design
   :land-acquisition
   :construction
   :other])

(def ^{:doc "Selectable pre-defined phase statuses"}
  phase-statuses
  [:in-progress
   :research
   :completed
   :valid
   :canceled
   :expired
   :other])
