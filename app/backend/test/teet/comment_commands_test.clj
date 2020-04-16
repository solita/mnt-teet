(ns ^:db teet.comment-commands-test
  (:require [clojure.test :refer :all]
            [teet.test.utils :as tu]))

(use-fixtures :once tu/with-environment (tu/with-db))
(use-fixtures :each tu/with-global-data)


(deftest commenting-tasks-requires-authorization
  (tu/create-task {:user tu/mock-user-boss :activity (tu/->db-id "p1-lc1-act1")} :task-id)
  (is (some? (tu/get-data :task-id)))
  (testing "Boss can comment the task they created"
    (tu/create-comment {:user tu/mock-user-boss
                        :entity-type :task
                        :entity-id (tu/get-data :task-id)
                        :comment {:comment/comment "Boss comment"}}
                       :boss-comment-id)
    (is (some? (tu/get-data :boss-comment-id))))
  (testing "External consultant can't comment the task as they don't have proper authorization"
    (is (thrown? Exception
                 (tu/create-comment {:user tu/mock-user-edna-consultant
                                     :entity-type :task
                                     :entity-id (tu/get-data :task-id)
                                     :comment {:comment/comment "Boss comment"}})))))
