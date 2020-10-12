(ns ^:db teet.link.link-queries-test
  (:require [clojure.test :refer :all]
            [teet.link.link-queries :as link-queries]
            [teet.test.utils :as tu]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(deftest search-task
  (testing "no matches if no tasks are created"
    (is (= (link-queries/search-task (tu/db)
                                     (tu/->db-id "p1")
                                     :en
                                     "Feasibility")
           [])))

  ;; Create a task for the project
  (tu/create-task {:user tu/mock-user-boss
                   :activity (tu/->db-id "p1-lc1-act1")
                   :task {:task/type :task.type/plot-allocation-plan
                          :task/group :task.group/land-purchase}}
                  :task-id)

  (testing "no matches if existing task names don't match the query text"
    (is (= (link-queries/search-task (tu/db)
                                     (tu/->db-id "p1")
                                     :en
                                     "Feasibility")
           [])))

  (testing "returns tasks whose names match given query"
    (let [matching-tasks (link-queries/search-task (tu/db)
                                                   (tu/->db-id "p1")
                                                   :en
                                                   "allocation")]
      (is (= (count matching-tasks) 1))
      (is (= (:db/id (first matching-tasks)) (tu/get-data :task-id))))))
