(ns teet.project.project-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk/id string?)

(s/def :activity/activity (s/keys))

(s/def :project/update-activity (s/keys :req [:activity/status]))

(s/def :project/create-activity
  (s/keys :req [:activity/name :activity/status
                :activity/estimated-start-date
                :activity/estimated-end-date]))

(s/def :project/initialization-form
  (s/keys :req [:thk.project/project-name
                :thk.project/km-range
                :thk.project/owner
                :thk.project/manager]))

(s/def :project/edit-form
  (s/keys :req [:thk.project/project-name
                :thk.project/owner
                :thk.project/manager]))
