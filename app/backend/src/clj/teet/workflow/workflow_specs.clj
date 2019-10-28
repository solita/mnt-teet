(ns teet.workflow.workflow-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk/id string?)
(s/def :workflow/name string?)

(s/def :workflow/phases (s/coll-of :phase/phase))

(s/def :phase/phase (s/keys))

(s/def :phase/update-phase (s/keys :req [:phase/status]))

(s/def :phase/create-phase
  (s/keys :req [:thk/id :phase/phase-name :phase/status
                :phase/estimated-start-date :phase/estimated-end-date]))
