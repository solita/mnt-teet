(ns teet.task.task-spec
  "Specs for task data"
  (:require [clojure.spec.alpha :as s]))

(s/def :task/type keyword?)
(s/def :task/description string?)
(s/def :task/assignee (s/keys :req [:user/id] :optn [:user/email :user/family-name :user/given-name]))

(s/def :task/new-task-form (s/keys :req [:task/type
                                         :task/description
                                         :task/assignee]))
