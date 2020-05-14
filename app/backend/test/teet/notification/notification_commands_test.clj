(ns ^:db teet.notification.notification-commands-test
  (:require [clojure.test :refer [is deftest testing] :as t]
            [teet.test.utils :as tu]
            teet.notification.notification-commands
            [teet.util.datomic :as du]))


(t/use-fixtures :once
  tu/with-environment
  (tu/with-db)
  tu/with-global-data)

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
                            :task/type :task.type/allotment-plan}}
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

  (testing "Carla has 2 unread comment notifications"
    (let [fetch-comment-notifications
          (fn []
            (filter #(du/enum= :notification.type/comment-created
                        (:notification/type %))
                    (tu/local-query tu/mock-user-carla-consultant
                                    :notification/user-notifications
                                    {})))
          notifications (fetch-comment-notifications)]
      (is (= 2 (count notifications)))
      (is (every? #(du/enum= :notification.status/unread
                             (:notification/status %)) notifications))
      (testing "Marking one as read will mark both"
        (tu/local-command [:user/id tu/external-consultant-id]
                          :notification/acknowledge
                          {:notification-id (:db/id (rand-nth notifications))})
        (let [notifications-after (fetch-comment-notifications)]
          (is (= 2 (count notifications-after)))
          (is (every? #(du/enum= :notification.status/acknowledged
                                 (:notification/status %))
                      notifications-after)))))))
