(ns ^:db teet.comment-commands-test
  (:require [clojure.test :refer :all]
            [datomic.client.api :as d]
            teet.comment.comment-commands
            [teet.comment.comment-model :as comment-model]
            teet.project.project-commands
            [teet.test.utils :as tu]
            [teet.util.datomic :as du]
            teet.integration.integration-s3))

(use-fixtures :each
  (tu/with-config {:file {:allowed-suffixes #{"pdf"}
                          :image-suffixes #{"png"}}})
  tu/with-environment
  (tu/with-db)
  tu/with-global-data)


(deftest commenting-tasks-requires-authorization
  (let [task-id (tu/create-task {:user tu/mock-user-boss
                                 :activity (tu/->db-id "p1-lc1-act1")
                                 :task {:task/type :task.type/plot-allocation-plan
                                        :task/group :task.group/land-purchase}}
                                :task-id)]
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
                                     :comment {:comment "Boss comment"}}))))

  ;; Boss invites the external consultant to the project
  (tu/local-command tu/mock-user-boss
                    :thk.project/add-permission
                    {:project-id (tu/->db-id "p1")
                     ;; Carla Consultant
                     :user {:user/person-id "EE33445566770"}
                     :role :external-consultant})

  (testing "External consultant can comment the task after being invited to the project"
    (let [external-comment-id (tu/create-comment {:user tu/mock-user-carla-consultant
                                                  :entity-type :task
                                                  :entity-id (tu/get-data :task-id)
                                                  :comment {:comment "Consultant comment"}})]
      (is (some? external-comment-id))))

  (testing "External consultant can only make comments that are publicly seen"
    (is (thrown? Exception
                 (tu/create-comment {:user tu/mock-user-carla-consultant
                                     :entity-type :task
                                     :entity-id (tu/get-data :task-id)
                                     :comment {:comment "Consultant comment"
                                               :visibility :comment.visibility/internal}})))))


(deftest comment-status-tracking
  ;; Update activity and set manager
  (tu/local-command tu/mock-user-boss
                    :activity/update
                    {:activity (merge
                                (d/pull (tu/db) '[:db/id :activity/estimated-start-date :activity/estimated-end-date]
                                        (tu/->db-id "p1-lc1-act1"))
                                {:activity/manager {:user/id tu/manager-id}})})

  ;; Create a task for commenting
  (let [task-id (tu/create-task {:user tu/mock-user-manager
                                 :activity (tu/->db-id "p1-lc1-act1")
                                 :task {:task/type :task.type/plot-allocation-plan
                                        :task/group :task.group/land-purchase}}
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
                                               {:eid (tu/get-data :task-id)
                                                :for :task})
                               (du/find-by-id (tu/get-data :tracked-comment-id)))]
      (is (= (-> created-comment :comment/status :db/ident)
             :comment.status/unresolved))))

  (testing "External consultant cannot track the comment status"
    ;; Boss invites external consultant Edna to the project.
    (tu/local-command tu/mock-user-boss
                      :thk.project/add-permission
                      {:project-id (tu/->db-id "p1")
                       ;; Carla Consultant
                       :user {:user/person-id "EE33445566770"}
                       :role :external-consultant})

    ;; Edna creates comment with tracking on...
    (tu/create-comment {:user tu/mock-user-carla-consultant
                        :entity-type :task
                        :entity-id (tu/get-data :task-id)
                        :comment {:comment "Consultant comment"
                                  ;; ... as can be seen here ...
                                  :track? true}}
                       :ednas-comment-id)

    ;; ... but the comment status is untracked
    (let [created-comment (->> (tu/local-query tu/mock-user-boss :comment/fetch-comments
                                               {:eid (tu/get-data :task-id)
                                                :for :task})
                               (du/find-by-id (tu/get-data :ednas-comment-id)))]
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
                                                 {:eid (tu/get-data :task-id)
                                                  :for :task})
                                 (du/find-by-id (tu/get-data :tracked-comment-id)))]
       (testing "the comment is status has changed"
         (is (= :comment.status/resolved
                (-> resolved-comment :comment/status :db/ident))))

       (testing "the comment is marked as having been modified"
         (is (contains? resolved-comment :meta/modifier))
         (is (contains? resolved-comment :meta/modified-at))))))

  (testing "Project manager can resolve all comments of an entity at once:"
    ;; Create a new task for multi resolve
    (let [task-id (tu/create-task {:user tu/mock-user-manager
                                   :activity (tu/->db-id "p1-lc1-act1")
                                   :task {:task/type :task.type/plot-allocation-plan
                                          :task/group :task.group/land-purchase}}
                                  :multi-resolve-task-id)]
      (is (some? task-id)))

    ;; Project manager adds 3 tracked comments ...
    (dotimes [n 3]
      (tu/create-comment {:user tu/mock-user-manager
                          :entity-type :task
                          :entity-id (tu/get-data :multi-resolve-task-id)
                          :comment {:comment (str "Tracked comment number " n)
                                    :track? true}}
                         (keyword (str "tracked-comment-" n))))

    ;; ... and 1 untracked one.
    (tu/create-comment {:user tu/mock-user-manager
                        :entity-type :task
                        :entity-id (tu/get-data :multi-resolve-task-id)
                        :comment {:comment (str "This one is NOT tracked")
                                  :track? false}}
                       :untracked-comment)

    (testing "before resolving all comments of task, the tracked comments are unresolved"
      (let [task-comments (tu/local-query tu/mock-user-manager :comment/fetch-comments
                                          {:eid (tu/get-data :multi-resolve-task-id)
                                           :for :task})]
        (is (= 3 (count (filter comment-model/unresolved? task-comments))))))

    (testing "after resolving all comments of task"
      (tu/local-command tu/mock-user-manager
                        :comment/resolve-comments-of-entity
                        {:entity-id (tu/get-data :multi-resolve-task-id)
                         :entity-type :task})
      (let [task-comments (tu/local-query tu/mock-user-manager :comment/fetch-comments
                                          {:eid (tu/get-data :multi-resolve-task-id)
                                           :for :task})]
        (testing "the tracked comments are resolved"
          (is (= 3 (count (filter comment-model/resolved? task-comments)))))


        (testing "untracked comments remain untracked"
          (is (= 1 (count (filter comment-model/untracked? task-comments))))))))

  (testing "External consultant cannot resolve all comments of an entity at once"
    ;; Create a new task for multi resolve
    (let [task-id (tu/create-task {:user tu/mock-user-manager
                                   :activity (tu/->db-id "p1-lc1-act1")
                                   :task {:task/type :task.type/plot-allocation-plan
                                          :task/group :task.group/land-purchase}}
                                  :external-multi-resolve-task-id)]
      (is (some? task-id)))

    (dotimes [n 3]
      (tu/create-comment {:user tu/mock-user-carla-consultant
                          :entity-type :task
                          :entity-id (tu/get-data :external-multi-resolve-task-id)
                          :comment {:comment (str "Tracked comment number " n)
                                    :track? true}}
                         (keyword (str "external-tracked-comment-" n))))

    (is (thrown? Exception
                 (tu/local-command tu/mock-user-carla-consultant
                                   :comment/resolve-comments-of-entity
                                   {:entity-id (tu/get-data :external-multi-resolve-task-id)
                                    :entity-type :task})))))

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
                   :task {:task/type :task.type/plot-allocation-plan
                          :task/group :task.group/land-purchase
                          :task/assignee {:user/id (second tu/mock-user-carla-consultant)}}} :task-id)
  ;; Create new file
  (is (= (second tu/mock-user-carla-consultant)
         (get-in (du/entity (tu/db) (tu/get-data :task-id))
                 [:task/assignee :user/id]))
      "task is assigned to carla")
  (tu/local-login tu/mock-user-carla-consultant)
  (with-redefs [;; Mock out URL generation (we don't use it for anything)
                teet.integration.integration-s3/presigned-url (constantly "url")]
    (->> (tu/local-command :file/upload {:task-id (tu/get-data :task-id)
                                         :file {:file/name "land_deals.pdf"
                                                :file/size 666}})
         :file
         (tu/store-data! :file)))

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
