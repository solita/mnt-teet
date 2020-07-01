(ns teet.project.project-specs
  (:require [clojure.spec.alpha :as s]))

(s/def :thk/id string?)

(s/def :thk.project/id string?)

(s/def :thk.project/skip-setup (s/keys :req [:thk.project/id]))

(s/def :thk.project/continue-setup (s/keys :req [:thk.project/id]))

(s/def :activity/activity (s/keys))

(s/def :activity/estimated-start-date inst?)
(s/def :activity/estimated-end-date inst?)

(s/def ::activity
  (s/keys :req [:activity/name
                :activity/estimated-start-date
                :activity/estimated-end-date]))

(s/def :activity/tasks-to-add (s/coll-of vector? :min-count 1))

(s/def :activity/add-tasks
  (s/keys :req [:db/id ;; Activity id
                ;; For all the tasks
                :task/estimated-start-date
                :task/estimated-end-date
                :activity/tasks-to-add]))

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
  (s/keys :req [(or :project/participant :user/person-id)
                :permission/role]))


(s/def :thk.project/fetch-project
  (s/keys :req [:thk.project/id]))
