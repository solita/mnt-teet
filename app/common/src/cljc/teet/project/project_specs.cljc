(ns teet.project.project-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk/id string?)

(s/def :thk.project/id string?)

(s/def :thk.project/skip-setup (s/keys :req [:thk.project/id]))

(s/def :thk.project/continue-setup (s/keys :req [:thk.project/id]))

(s/def :activity/activity (s/keys))

(s/def :activity/update (s/keys :req [:activity/status]))

(s/def ::activity
  (s/keys :req [:activity/name :activity/status
                :activity/estimated-start-date
                :activity/estimated-end-date]))

(s/def :activity/create
  (s/keys :req-un [::activity ::tasks ::lifecycle-id]))

(s/def :project/initialization-form
  (s/keys :req [:thk.project/project-name
                :thk.project/km-range
                :thk.project/owner
                :thk.project/manager]))

(s/def :project/edit-form
  (s/keys :req [:thk.project/project-name
                :thk.project/owner
                :thk.project/manager]))

(s/def :project/edit-details-form
  (s/keys :req [:thk.project/project-name]))

(s/def :project/add-permission-form
  (s/keys :req [:project/participant
                :permission/role]))


(s/def :thk.project/fetch-project
  (s/keys :req [:thk.project/id]))
