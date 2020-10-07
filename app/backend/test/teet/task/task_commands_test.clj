(ns ^:db teet.task.task-commands-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.test.utils :as tu]
            teet.task.task-commands
            [datomic.client.api :as d]
            [teet.util.datomic :as du])
  (:import (java.util Date)))

(t/use-fixtures
  :each
  tu/with-environment
  (tu/with-db))

(deftest creating-task-possible-within-activity-dates
  (tu/local-login tu/mock-user-boss)
  (let [activity-id (tu/->db-id "p1-lc1-act1")
        activity-entity (du/entity (tu/db) activity-id)
        task-id (tu/create-task
                 {:activity activity-id
                  :task {:task/group :task.group/land-purchase
                         :task/type :task.type/plot-allocation-plan
                         :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                         :task/estimated-start-date (:activity/estimated-start-date activity-entity)
                         :task/estimated-end-date (:activity/estimated-end-date activity-entity)}})]
    (is (some? task-id) "Task was created successfully")))

(deftest creating-task-not-possible-outside-activity-dates
  (tu/local-login tu/mock-user-boss)
  (let [activity-id (tu/->db-id "p1-lc1-act1")
        activity-entity (du/entity (tu/db) activity-id)
        before-start-date (Date. (- (.getTime (:activity/estimated-start-date activity-entity)) (* 1000 60 60 24)))
        after-end-date (Date. (+ (.getTime (:activity/estimated-end-date activity-entity)) (* 1000 60 60 24)))]
    (testing "Can not create task with start date outside of activity start date"
      (is (thrown? Exception
                   (tu/create-task
                    {:activity activity-id
                     :task {:task/group :task.group/land-purchase
                            :task/type :task.type/plot-allocation-plan
                            :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                            :task/estimated-start-date before-start-date
                            :task/estimated-end-date (:activity/estimated-end-date activity-entity)}}))))
    (testing "Can not create task with end date outside of activity end date"
      (is (thrown? Exception
                   (tu/create-task
                    {:activity activity-id
                     :task {:task/group :task.group/land-purchase
                            :task/type :task.type/plot-allocation-plan
                            :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                            :task/estimated-start-date (:activity/estiated-start-date activity-entity)
                            :task/estimated-end-date after-end-date}}))))))

(deftest task-assignment-creates-notification-to-assignee
  (tu/local-login tu/mock-user-boss)
  (let [activity-id (tu/->db-id "p1-lc1-act1")
        activity-entity (du/entity (tu/db) activity-id)
        task-id
        (tu/create-task
         {:activity activity-id
          :task {:task/group :task.group/land-purchase
                 :task/type :task.type/plot-allocation-plan
                 :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                 :task/estimated-start-date (:activity/estimated-start-date activity-entity)
                 :task/estimated-end-date (:activity/estimated-end-date activity-entity)}})]
    (is (some? task-id) "task was created")

    (testing "Edna has new notification targeting the task"
      (let [notification (ffirst
                          (d/q '[:find (pull ?n [*])
                                 :where [?n :notification/receiver ?edna]
                                 :in $ ?edna]
                               (tu/db) tu/mock-user-edna-consultant))]
        (is (some? notification) "Edna has one notification")
        (is (= {:notification/status :notification.status/unread
                :notification/type :notification.type/task-assigned
                :notification/target {:db/id task-id}}
               (-> notification
                   (select-keys [:notification/status :notification/type
                                 :notification/target])
                   (update :notification/status :db/ident)
                   (update :notification/type :db/ident)))
            "notification is unread and targets the task")

        (testing "Edna can fetch the notification nav info"
          (tu/local-login tu/mock-user-edna-consultant)
          (let [nav-info (tu/local-query :notification/navigate
                                         {:notification-id (:db/id notification)})]
            (is (= {:page :activity-task
                    :params {:activity (str activity-id)
                             :task (str task-id)
                             :project "11111"}}
                   nav-info)
                "navigation info is for the task page")))))))

(deftest task-type-must-be-allowed-for-the-parent-activity
  (tu/local-login tu/mock-user-boss)
  (testing "Benjamin cannot create a feasibility study task for a land acquisition activity"
    (let [activity-id (tu/->db-id "p1-lc1-act1")
          activity-entity (du/entity (tu/db) activity-id)]
      (is (thrown? Exception
                   (tu/create-task
                    {:activity activity-id
                     :task {:task/group :task.group/study
                            :task/type :task.type/feasibility-study
                            :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                            :task/estimated-start-date (:activity/estimated-start-date activity-entity)
                            :task/estimated-end-date (:activity/estimated-end-date activity-entity)}}))))))
