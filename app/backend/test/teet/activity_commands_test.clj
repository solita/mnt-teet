(ns ^:db teet.activity-commands-test
  (:require [clojure.test :refer :all]
            [datomic.client.api :as d]
            [teet.test.utils :as tu]
            [teet.util.collection :as cu]
            [teet.task.task-db :as task-db]
            [teet.util.datomic :as du]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)


(defn act1-id []
  (tu/->db-id "p1-lc1-act1"))

(defn act1-status []
  (->> (act1-id) (d/pull (tu/db) '[:activity/status] ) :activity/status :db/ident))

(deftest activity-submit-statuses
  ;; Set manager
  (tu/local-command tu/mock-user-boss
                    :activity/update
                    {:activity {:db/id (act1-id)
                                :activity/manager {:user/id tu/manager-id}}})

  (tu/local-command tu/mock-user-boss
                    :thk.project/add-permission
                    {:project-id (tu/->db-id "p1")
                     ;; Carla Consultant
                     :user {:user/person-id "EE33445566770"}
                     :role :ta-consultant})

  (let [task-id (tu/create-task {:user tu/mock-user-manager :activity (act1-id)
                                 :task {:task/type :task.type/third-party-review
                                        :task/group :task.group/land-purchase}})]
    (is (number? task-id))

    (tu/complete-task {:user tu/mock-user-manager
                       :task-id task-id}))

  (testing "activity submission happy path yields expected status changes"
    (is (= :activity.status/in-progress (act1-status)))
    (tu/local-login tu/mock-user-carla-consultant)
    (is (thrown? Exception
                 (tu/local-command :activity/submit-for-review {:activity-id (act1-id)})))
    (is (= :activity.status/in-progress (act1-status)))
    ;; project manager required for submission
    (tu/local-login tu/mock-user-manager)
    (tu/local-command :activity/submit-for-review {:activity-id (act1-id)})
    (is (= :activity.status/in-review (act1-status)))
    (tu/local-login tu/mock-user-edna-consultant)
    (is (thrown? Exception
                 (tu/local-command :activity/review {:activity-id (act1-id) :status :activity.status/completed})))
    ;; project owner is required for review
    (tu/local-login tu/mock-user-manager)
    (is (= :activity.status/in-review (act1-status)))
    (tu/local-login tu/mock-user-manager)
    (tu/local-command :activity/review {:activity-id (act1-id) :status :activity.status/completed})
    (is (= :activity.status/completed (act1-status)))))


;; repl compatible fixtureless draft version below for later repl iteration
#_(do
  (def *gdata (atom {}))
  (def *actid 56937110132752661)
  (defn repltest []
    (tu/local-login tu/mock-user-manager)
    (binding [tu/*global-test-data* *gdata]
      (let [task-id (tu/create-task {:user tu/mock-user-manager :activity *actid} :task-id)]
        (is (some? task-id)))
      (doseq [task-id-map (:activity/tasks (d/pull (tu/db) '[:activity/name :activity/tasks] *actid))]
        (tu/complete-task {:user tu/mock-user-manager
                           :task-id (:db/id task-id-map)}))

      (tu/local-command :activity/submit-for-review {:activity-id *actid :user tu/mock-user-manager})
      (tu/local-command :activity/review {:activity-id *actid :user tu/mock-user-manager :status :activity.status/completed})

      (println (= :completed (->> *actid (d/pull (tu/db) '[:activity/status] ) :activity/status :db/ident ))))))

(deftest new-activity
  (testing "Can't create activity for a nonexistent lifecycle"
    (is (thrown? Exception
                 (tu/local-command tu/mock-user-boss
                                   :activity/create
                                   {:activity {:activity/estimated-start-date #inst "2020-04-06T21:00:00.000-00:00"
                                               :activity/estimated-end-date #inst "2020-04-12T21:00:00.000-00:00"
                                               :activity/name :activity.name/land-acquisition}
                                    :tasks [[:task.group/base-data :task.type/general-part false]]
                                    :lifecycle-id 12345}))))

  (testing "Can't create activity if an activity of the same type already exists in the lifecycle"
    (is (thrown? Exception
                 (tu/local-command tu/mock-user-boss
                                   :activity/create
                                   {:activity {:activity/estimated-start-date #inst "2020-04-06T21:00:00.000-00:00"
                                               :activity/estimated-end-date #inst "2020-04-12T21:00:00.000-00:00"
                                               :activity/name :activity.name/land-acquisition}
                                    :tasks [[:task.group/base-data :task.type/general-part false]]
                                    :lifecycle-id (tu/->db-id "p1-lc1")}))))

  (testing "Can't add incompatible tasks to new activity"
    (is (thrown? Exception
                 (tu/local-command tu/mock-user-boss
                                   :activity/create
                                   {:activity {:activity/estimated-start-date #inst "2020-04-06T21:00:00.000-00:00"
                                               :activity/estimated-end-date #inst "2020-04-12T21:00:00.000-00:00"
                                               :activity/name :activity.name/land-acquisition}
                                    :tasks [[:task.group/base-data :task.type/general-part false]]
                                    :lifecycle-id (tu/->db-id "p3-lc1")})))
    (is (tu/local-command tu/mock-user-boss
                          :activity/create
                          {:activity {:activity/estimated-start-date #inst "2020-04-12T21:00:00.000-00:00"
                                      :activity/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                                      :activity/name :activity.name/land-acquisition}
                           :tasks [[:task.group/land-purchase :task.type/preliminary-agreements false]]
                           :lifecycle-id (tu/->db-id "p3-lc1")}))))

(deftest add-tasks
  (is (thrown? Exception
               (tu/local-command tu/mock-user-boss
                                 :activity/add-tasks
                                 {:db/id 12345 ;; made up activity id
                                  :task/estimated-start-date #inst "2020-04-11T21:00:00.000-00:00" ;; Oops, too early
                                  :task/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                                  :activity/tasks-to-add [[:task.group/land-purchase :task.type/cadastral-works false]
                                                          [:task.group/land-purchase :task.type/property-valuation false]]}))
      "Tasks can only be added to an existing activity")

  (tu/store-data! :new-activity-id
                  (-> (tu/local-command tu/mock-user-boss
                                        :activity/create
                                        {:activity {:activity/estimated-start-date #inst "2020-04-12T21:00:00.000-00:00"
                                                    :activity/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                                                    :activity/name :activity.name/land-acquisition}
                                         :tasks [[:task.group/land-purchase :task.type/third-party-review false]]
                                         :lifecycle-id (tu/->db-id "p3-lc1")})
                      :tempids
                      (get "new-activity")))

  (tu/store-data! :old-activity-count (-> (du/entity (tu/db) (tu/get-data :new-activity-id))
                                          :activity/tasks
                                          count))
  (is (thrown? Exception
               (tu/local-command tu/mock-user-boss
                                 :activity/add-tasks
                                 {:db/id (tu/get-data :new-activity-id)
                                  :task/estimated-start-date #inst "2020-04-12T21:00:00.000-00:00"
                                  :task/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                                  :activity/tasks-to-add [[:task.group/design :task.type/buildings false]
                                                          [:task.group/base-data :task.type/technical-conditions false]]}))
      "Tasks' estimated start and end dates need to occur within acitivity's start and end dates")


  (is (thrown? Exception
               (tu/local-command tu/mock-user-boss
                                 :activity/add-tasks
                                 {:db/id (tu/get-data :new-activity-id)
                                  :task/estimated-start-date #inst "2020-04-11T21:00:00.000-00:00" ;; Oops, too early
                                  :task/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                                  :activity/tasks-to-add [[:task.group/land-purchase :task.type/third-party-review false]]}))
      "Can't create a task with the same type as a not-finished existing task")

  (is (tu/local-command tu/mock-user-boss
                        :activity/add-tasks
                        {:db/id (tu/get-data :new-activity-id)
                         :task/estimated-start-date #inst "2020-04-12T22:00:00.000-00:00"
                         :task/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                         :activity/tasks-to-add [[:task.group/land-purchase :task.type/state-estate-registry false]
                                                 [:task.group/land-purchase :task.type/plot-allocation-plan false]]})
      "New tasks are added")

  (is (= (-> (du/entity (tu/db) (tu/get-data :new-activity-id)) :activity/tasks count)
         (+ 2 (tu/get-data :old-activity-count)))
      "Two tasks were added"))

(defn create-new-activity
  "Create new test Activity"
  []
  (tu/store-data! :new-activity-id
    (-> (tu/local-command tu/mock-user-boss
          :activity/create
          {:activity {:activity/estimated-start-date #inst "2020-04-12T21:00:00.000-00:00"
                      :activity/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                      :activity/name :activity.name/land-acquisition}
           :tasks [[:task.group/land-purchase :task.type/third-party-review false]]
           :lifecycle-id (tu/->db-id "p3-lc1")})
      :tempids
      (get "new-activity"))))

(defn create-task-to-activity
  "Create new task to activity with :new-activity-id and store new task id as :task-id"
  []
  (tu/store-data!
    :task-id
    (tu/create-task {:user tu/mock-user-boss
                     :activity (tu/get-data :new-activity-id)
                     :task {:task/type :task.type/cadastral-works
                            :task/group :task.group/land-purchase}})))

(deftest delete-activity
  (testing "Activity can be deleted"
    (tu/give-admin-permission tu/mock-user-boss)
    (create-new-activity)
    (tu/local-command tu/mock-user-boss
                      :activity/delete
                      {:db/id (tu/get-data :new-activity-id)})
    (is (:meta/deleted? (du/entity (tu/db)
                                   (tu/get-data :new-activity-id))))))

(deftest delete-activity-only-by-admin
  (testing "Activity delete fails without Admin role authorization"
    (create-new-activity)
    (is (thrown? Exception
          (tu/local-command tu/mock-user-boss
            :activity/delete
            {:db/id (tu/get-data :new-activity-id)})))
    (is (not (:meta/deleted?
               (du/entity (tu/db)
                 (tu/get-data :new-activity-id)))))
    (is (thrown-with-msg?
          Exception #"Request authorization failed"
          (tu/local-command tu/mock-user-boss
            :activity/delete
            {:db/id (tu/get-data :new-activity-id)})))

    (create-task-to-activity)

    (testing "Activity delete successful for Admin role authorized"
      (tu/give-admin-permission tu/mock-user-boss)

      (testing " Activity Task in not deleted initially"
        (is (not (:meta/deleted?
                   (du/entity (tu/db)
                     (tu/get-data :task-id))))))

      (tu/local-command tu/mock-user-boss
        :activity/delete
        {:db/id (tu/get-data :new-activity-id)})

      (testing "Check Activity marked as deleted"
        (is (:meta/deleted?
              (du/entity (tu/db)
                (tu/get-data :new-activity-id)))))

      (testing "Activity Task also marked for deletion"
        (is (:meta/deleted?
              (du/entity (tu/db)
                (tu/get-data :task-id))))))))


;; TODO: add fake file upload to Activity's Task
(deftest delete-activity-only-without-files-in-tasks
  (testing "Activity can only be deleted without files attached to Tasks"
    (create-new-activity)
    (tu/give-admin-permission tu/mock-user-boss)
    (tu/local-command tu/mock-user-boss
      :activity/delete
      {:db/id (tu/get-data :new-activity-id)})
    (is (:meta/deleted?
          (du/entity (tu/db)
            (tu/get-data :new-activity-id))))))


(deftest assigning-pm-gives-permission
  (let [current-manager #(get-in (tu/entity (act1-id))
                           [:activity/manager :user/id] :no-manager)]
    (testing "Initially p1 doesn't have a manager"
      (is (= :no-manager (current-manager))))

    (testing "Assigning p1 lc1 act1 manager"
      (tu/local-command tu/mock-user-boss
        :activity/update
        {:activity {:db/id (act1-id)
                    :activity/manager {:user/id (second tu/mock-user-edna-consultant)}}})

      (testing "sets the new manager"
        (is (= (second tu/mock-user-edna-consultant)
              (current-manager))))


      (testing "gives the new pm permission"
        (let [permissions (d/q '[:find (pull ?p [:permission/valid-from])
                                 :where
                                 [?user :user/permissions ?p]
                                 [?p :permission/role :ta-project-manager]
                                 [?p :permission/projects ?project]
                                 [?p :permission/valid-from ?valid-from]
                                 [(.before ?valid-from ?now)]
                                 [(missing? $ ?p :permission/valid-until)]
                                 :in
                                 $ ?user ?project ?now]
                            (tu/db)
                            tu/mock-user-edna-consultant
                            [:thk.project/id "11111"]
                            (java.util.Date.))]
          (is (seq permissions) "permission is present"))))))

(deftest two-thk-tasks-with-same-group-is-valid
  (is
   (task-db/valid-tasks?
    (tu/db)
    :activity.name/preliminary-design
    '([:task.group/study :task.type/archeological-study true]
     [:task.group/study :task.type/topogeodesy true]))))

(deftest can-not-create-thk-tasks-in-teet
  (testing "Tasks are not valid when list of tasks contains road-safety-audit or supervision"
    (is
      (not
        (task-db/valid-tasks?
          (tu/db)
          :activity.name/preliminary-design
          '([:task.group/construction-quality-assurance :task.type/owners-supervision false]
            [:task.group/construction-approval :task.type/road-safety-audit false]))))))
