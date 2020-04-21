(ns ^:db teet.comment-commands-test
  (:require [clojure.test :refer :all]
            [teet.test.utils :as tu]
            [teet.util.datomic :as du]
            [teet.permission.permission-db :as permission-db]
            [datomic.client.api :as d]))

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

(deftest comment-on-file-notification
  ;; Give Carla external consultant to p1
  (tu/tx {:user/id (second tu/mock-user-carla-consultant)
          :user/permissions [{:db/id "new-permission"
                              :permission/projects [(tu/->db-id "p1")]
                              :permission/role :external-consultant
                              :permission/valid-from (java.util.Date.)}]})

  ;; Create task in first activity
  (tu/create-task {:user tu/mock-user-boss
                   :activity (tu/->db-id "p1-lc1-act1")
                   :task {:task/type :task.type/land-owners
                          :task/assignee {:user/id (second tu/mock-user-carla-consultant)}}} :task-id)
  ;; Create new file
  (is (= (second tu/mock-user-carla-consultant)
         (get-in (du/entity (tu/db) (tu/get-data :task-id))
                 [:task/assignee :user/id]))
      "task is assigned to carla")
  (tu/local-login tu/mock-user-carla-consultant)
  (->> (tu/local-command :file/upload {:task-id (tu/get-data :task-id)
                                       :file {:file/name "land_deals.pdf"
                                              :file/size 666
                                              :file/type "application/pdf"}})
       :file
       (tu/store-data! :file))

  (is (some? (:db/id (tu/get-data :file))) "file was created")

  (testing "Someone else comments on file created by Carla"
    (tu/local-command tu/mock-user-boss
                      :comment/create
                      {:entity-id (:db/id (tu/get-data :file))
                       :entity-type :file
                       :comment "very nice file 5/5"
                       :visibility :comment.visibility/all})

    (let [{:notification/keys [target]}
          (ffirst
           (d/q '[:find (pull ?n [*])
                  :where
                  [?n :notification/type :notification.type/comment-created]
                  [?n :notification/receiver ?carla]
                  :in $ ?carla]
                (tu/db) tu/mock-user-carla-consultant))]
      (is (= (get-in (du/entity (tu/db) (:db/id target))
                     [:file/_comments  0 :db/id])
             (:db/id (tu/get-data :file)))
          "Notification is about comment on file"))))
