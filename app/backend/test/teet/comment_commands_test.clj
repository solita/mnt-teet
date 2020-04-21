(ns ^:db teet.comment-commands-test
  (:require [clojure.test :refer :all]
            teet.comment.comment-commands
            teet.project.project-commands
            [teet.test.utils :as tu]
            [teet.util.collection :as cu]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)


(deftest commenting-tasks-requires-authorization
  (let [task-id (tu/create-task {:user tu/mock-user-boss :activity (tu/->db-id "p1-lc1-act1")} :task-id)]
    (is (some? task-id)))

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
                                     :comment {:comment/comment "Boss comment"}}))))

  (testing "External consultant can comment the task after being invited to the project"
    (tu/local-command tu/mock-user-boss
                      :thk.project/add-permission
                      {:project-id (tu/->db-id "p1")
                       :user {:user/id tu/external-consultant-id}
                       :role :external-consultant})
    (let [external-comment-id (tu/create-comment {:user tu/mock-user-carla-consultant
                                                  :entity-type :task
                                                  :entity-id (tu/get-data :task-id)
                                                  :comment {:comment/comment "Consultant comment"}})]
      (is (some? external-comment-id)))))

(deftest comment-status-tracking
  (let [task-id (tu/create-task {:user tu/mock-user-boss
                                 :activity (tu/->db-id "p1-lc1-act1")}
                                :task-id)]
    (is (some? task-id)))

  (testing "Boss can choose to track the status of their comments"
    (tu/create-comment {:user tu/mock-user-boss
                        :entity-type :task
                        :entity-id (tu/get-data :task-id)
                        :comment {:comment "Boss's comment"
                                  :track? true}}
                       :tracked-comment-id)
    (is (some? (tu/get-data :tracked-comment-id)))
    (let [created-comment (->> (tu/local-query tu/mock-user-boss :comment/fetch-comments
                                               {:db/id (tu/get-data :task-id)
                                                :for :task})
                               (cu/find-by-id (tu/get-data :tracked-comment-id)))]
      (is (= (-> created-comment :comment/status :db/ident)
             :comment.status/unresolved))))

  (testing "External consultant cannot track the comment status"
    (tu/local-command tu/mock-user-boss
                      :thk.project/add-permission
                      {:project-id (tu/->db-id "p1")
                       :user {:user/id tu/external-consultant-id}
                       :role :external-consultant})

    ;; Edna creates comment with tracking on...
    (tu/create-comment {:user tu/mock-user-carla-consultant
                        :entity-type :task
                        :entity-id (tu/get-data :task-id)
                        :comment {:comment/comment "Consultant comment"
                                  ;; ... as can be seen here ...
                                  :track? true}}
                       :ednas-comment-id)

    ;; ... but the comment status is untracked
    (let [created-comment (->> (tu/local-query tu/mock-user-boss :comment/fetch-comments
                                               {:db/id (tu/get-data :task-id)
                                                :for :task})
                               (cu/find-by-id (tu/get-data :ednas-comment-id)))]
      (is (= :comment.status/untracked
             (-> created-comment :comment/status :db/ident))))))
