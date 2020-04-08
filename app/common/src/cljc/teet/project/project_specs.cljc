(ns teet.project.project-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk/id string?)

(s/def :activity/activity (s/keys))

(s/def :activity/update (s/keys :req [:activity/status]))

(s/def :activity/create
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

(s/def :project/add-permission-form
  (s/keys :req [:project/participant
                :permission/role]))


(s/def :thk.project/fetch-project
  (s/keys :req [:thk.project/id]))
