(ns teet.project.project-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk.project/id string?)

(s/def :thk.project/skip-setup (s/keys :req [:thk.project/id]))

(s/def :thk.project/continue-setup (s/keys :req [:thk.project/id]))
