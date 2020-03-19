(ns teet.task.task-spec
  "Specs for task data"
  (:require [clojure.spec.alpha :as s]))

(s/def :task/type keyword?)
(s/def :task/description string?)
(s/def :task/assignee (s/keys :req [:user/id]))

(s/def :task/new-task-form (s/keys :req [:task/group
                                         :task/type
                                         :task/description
                                         :task/assignee
                                         :task/estimated-start-date
                                         :task/estimated-end-date]))
