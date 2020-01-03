(ns teet.project.project-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk.project/id string?)

(s/def :project/skip-project-setup (s/keys :req [:thk.project/id]))

(s/def :project/continue-project-setup (s/keys :req [:thk.project/id]))
