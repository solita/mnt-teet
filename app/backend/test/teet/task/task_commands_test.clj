(ns teet.task.task-commands-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.test.utils :as tu]
            teet.task.task-commands
            [datomic.client.api :as d]
            [teet.util.date :as date]))

(t/use-fixtures
  :once
  tu/with-environment
  (tu/with-db))

(deftest task-assignment-creates-notification-to-assignee
  (tu/local-login (second tu/mock-user-boss))
  (let [activity-id (tu/->db-id "p1-lc1-act1")
        result
        (tu/local-command
         :task/create
         {:activity-id activity-id
          :task {:db/id "new-task"
                 :task/type :task.type/acceptances
                 :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                 :task/estimated-start-date (date/->date 2020 4 15)
                 :task/estimated-end-date (date/->date 2021 1 30)}})
        task-id (get-in result [:tempids "new-task"])]
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
          (tu/local-login (second tu/mock-user-edna-consultant))
          (let [nav-info (tu/local-query :notification/navigate
                                         {:notification-id (:db/id notification)})]
            (is (= {:page :activity-task
                    :params {:activity (str activity-id)
                             :task (str task-id)
                             :project "11111"}}
                   nav-info)
                "navigation info is for the task page")))))))
