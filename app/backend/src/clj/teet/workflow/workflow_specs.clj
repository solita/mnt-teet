(ns teet.workflow.workflow-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk/id string?)
(s/def :workflow/name string?)

(s/def :workflow/activities (s/coll-of :activity/activity))

(s/def :activity/activity (s/keys))

(s/def :activity/update-activity (s/keys :req [:activity/status]))

(s/def :activity/create-activity
  (s/keys :req [:activity/name :activity/status
                :activity/estimated-start-date
                :activity/estimated-end-date]))
