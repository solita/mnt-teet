(ns ^:db teet.notification.notification-commands-test
  (:require [clojure.test :refer [is deftest testing] :as t]
            [teet.test.utils :as tu]
            teet.notification.notification-commands
            [teet.util.datomic :as du]))


(t/use-fixtures :once
  tu/with-environment
  (tu/with-db)
  tu/with-global-data)

(defn fetch-user-comment-notifications
  [user]
  (filter #(du/enum= :notification.type/comment-created
                     (:notification/type %))
          (tu/local-query user
                          :notification/user-notifications
                          {})))

(deftest acknowledging-comment-notification
  (testing "Create task and comments for task"

    (tu/tx {:user/id tu/external-consultant-id
            :user/permissions [{:db/id "new-permission"
                                :permission/projects (tu/->db-id "p1")
                                :permission/valid-from (java.util.Date.)
                                :permission/role :external-consultant}]})

    (tu/create-task {:user tu/mock-user-boss
                     :activity (tu/->db-id "p1-lc1-act1")
                     :task {:task/group :task.group/land-purchase
                            :task/assignee {:user/id tu/external-consultant-id}
                            :task/type :task.type/property-valuation}}
                    :task-id)


    (tu/create-comment {:user tu/mock-user-carla-consultant
                        :entity-type :task
                        :entity-id (tu/get-data :task-id)
                        :comment {:comment/comment "comment to be participant"}})

    (doseq [c ["first comment" "another comment"]]
      (tu/create-comment {:user tu/mock-user-boss
                          :entity-type :task
                          :entity-id (tu/get-data :task-id)
                          :comment {:comment/comment c}})))


  (testing "Boss doesn't receive notifications for their own comments"
    (let [boss-notifications (fetch-user-comment-notifications tu/mock-user-boss)]
      (is (= 0 (count boss-notifications)))))

  (testing "Carla has received notifications for boss' comments"
    (let [carla-notifications (fetch-user-comment-notifications tu/mock-user-carla-consultant)]
      (is (= 2 (count carla-notifications)))
      (is (every? #(du/enum= :notification.status/unread
                             (:notification/status %)) carla-notifications))
      (testing "Marking one as read will mark both"
        (tu/local-command [:user/id tu/external-consultant-id]
                          :notification/acknowledge
                          {:notification-id (:db/id (rand-nth carla-notifications))})
        (let [notifications-after (fetch-user-comment-notifications tu/mock-user-carla-consultant)]
          (is (= 2 (count notifications-after)))
          (is (every? #(du/enum= :notification.status/acknowledged
                                 (:notification/status %))
                      notifications-after)))))))
