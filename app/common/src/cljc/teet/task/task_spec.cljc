(ns teet.task.task-spec
  "Specs for task data"
  (:require [clojure.spec.alpha :as s]))

(s/def :task/new-task-form (s/keys :req [:task/type
                                         :task/description
                                         :task/assignee]))
