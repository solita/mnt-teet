(ns teet.project.task-model-test
  (:require [clojure.test :refer [deftest is]]
            [teet.project.task-model :as task-model]))

(deftest task-submit-status
  (is (false? (task-model/can-submit? {:task/status :task.status/completed})))
  (is (false? (task-model/can-submit? {:task/status {:db/ident :task.status/completed}})))
  (is (true? (task-model/can-submit? {:task/status :task.status/in-progress})))
  (is (true? (task-model/can-submit? {:task/status {:db/ident :task.status/in-progress}}))))
