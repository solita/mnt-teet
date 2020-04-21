(ns ^:db teet.comment-commands-test
  (:require [clojure.test :refer :all]
            [datomic.client.api :as d]
            teet.comment.comment-commands
            teet.project.project-commands
            [teet.test.utils :as tu]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

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
  ;; Grant access to project manager
  (tu/local-command tu/mock-user-boss
                    :thk.project/add-permission
                    {:project-id (tu/->db-id "p1")
                     :user {:user/id tu/manager-id}
                     :role :manager})

  ;; Create a task for commenting
  (let [task-id (tu/create-task {:user tu/mock-user-manager
                                 :activity (tu/->db-id "p1-lc1-act1")}
                                :task-id)]
    (is (some? task-id)))

  (testing "Project manager can choose to track the status of their comments"
    (tu/create-comment {:user tu/mock-user-manager
                        :entity-type :task
                        :entity-id (tu/get-data :task-id)
                        :comment {:comment "Pm's comment"
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
    ;; Boss invites external consultant Edna to the project.
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
             (-> created-comment :comment/status :db/ident)))))

  (testing "Project manager can resolve and unresolve a comment:"
    ;; Only valid test statuses are allowed
    (testing "Only valid test statuses are allowed"
      (is (thrown? Exception
                   (tu/local-command tu/mock-user-manager
                                     :comment/set-status
                                     {:db/id (tu/get-data :tracked-comment-id)
                                      :comment/status :not-a-valid-status}))))

    (testing "When comment is resolved successfully"
      (tu/local-command tu/mock-user-manager
                        :comment/set-status
                        {:db/id (tu/get-data :tracked-comment-id)
                         :comment/status :comment.status/resolved})

     (let [resolved-comment (->> (tu/local-query tu/mock-user-manager :comment/fetch-comments
                                                 {:db/id (tu/get-data :task-id)
                                                  :for :task})
                                 (cu/find-by-id (tu/get-data :tracked-comment-id)))]
       (testing "the comment is status has changed"
         (is (= :comment.status/resolved
                (-> resolved-comment :comment/status :db/ident))))
       (testing "the comment is marked as having been modified"
         (is (contains? resolved-comment :meta/modifier))
         (is (contains? resolved-comment :meta/modified-at)))))))

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
