(ns teet.workflow.workflow-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk/id string?)
(s/def :workflow/name string?)

(s/def :workflow/phases (s/coll-of :phase/phase))

(s/def :phase/phase (s/keys))

(s/def :workflow/create-project-workflow
  (s/keys :req [:thk/id :workflow/name]
          :opt [:workflow/phases]))
