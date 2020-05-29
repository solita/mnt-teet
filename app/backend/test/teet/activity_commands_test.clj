(ns ^:db teet.activity-commands-test
  (:require [clojure.test :refer :all]
            [datomic.client.api :as d]
            teet.activity.activity-commands
            [teet.test.utils :as tu]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)


(defn act1-id []
  (tu/->db-id "p1-lc1-act1"))

(defn act1-status []
  (->> (act1-id) (d/pull (tu/db) '[:activity/status] ) :activity/status :db/ident))

(deftest activity-submit-statuses
  ;; Set manager
  (tu/local-command tu/mock-user-boss
                    :thk.project/update
                    {:thk.project/id "11111"
                     :thk.project/manager {:user/id tu/manager-id}})

  (tu/local-command tu/mock-user-boss
                    :thk.project/add-permission
                    {:project-id (tu/->db-id "p1")
                     :user {:user/id tu/internal-consultant-id}
                     :role :internal-consultant})

  (let [task-id (tu/create-task {:user tu/mock-user-manager :activity (act1-id)
                                 :task {:task/type :task.type/plot-allocation-plan}})]
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
                           :tasks [[:task.group/land-purchase :task.type/land-owners false]]
                           :lifecycle-id (tu/->db-id "p3-lc1")}))))

(deftest add-tasks
  (tu/store-data! :new-activity-id
                  (-> (tu/local-command tu/mock-user-boss
                                        :activity/create
                                        {:activity {:activity/estimated-start-date #inst "2020-04-12T21:00:00.000-00:00"
                                                    :activity/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                                                    :activity/name :activity.name/land-acquisition}
                                         :tasks [[:task.group/land-purchase :task.type/land-owners false]]
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
                                  :task/estimated-start-date #inst "2020-04-11T21:00:00.000-00:00" ;; Oops, too early
                                  :task/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                                  :activity/tasks-to-add [[:task.group/land-purchase :task.type/cadastral-works false]
                                                          [:task.group/land-purchase :task.type/plot-allocation-plan false]]}))
      "Tasks' estimated start and end dates need to occur within acitivity's start and end dates")

  (is (tu/local-command tu/mock-user-boss
                        :activity/add-tasks
                        {:db/id (tu/get-data :new-activity-id)
                         :task/estimated-start-date #inst "2020-04-12T22:00:00.000-00:00"
                         :task/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                         :activity/tasks-to-add [[:task.group/land-purchase :task.type/cadastral-works false]
                                                 [:task.group/land-purchase :task.type/plot-allocation-plan false]]})
      "New tasks are added")

  (is (= (-> (du/entity (tu/db) (tu/get-data :new-activity-id)) :activity/tasks count)
         (+ 2 (tu/get-data :old-activity-count)))
      "Two tasks were added"))

(deftest delete-activity
  (testing "Activity can be deleted if doesn't have a procurement number"
    (tu/store-data! :new-activity-id
                    (-> (tu/local-command tu/mock-user-boss
                                          :activity/create
                                          {:activity {:activity/estimated-start-date #inst "2020-04-12T21:00:00.000-00:00"
                                                      :activity/estimated-end-date #inst "2020-04-13T21:00:00.000-00:00"
                                                      :activity/name :activity.name/land-acquisition}
                                           :tasks [[:task.group/land-purchase :task.type/land-owners false]]
                                           :lifecycle-id (tu/->db-id "p3-lc1")})
                        :tempids
                        (get "new-activity")))

    ;; Let's give the activity a procurement number. In reality this would come from THK.
    (tu/tx {:db/id (tu/get-data :new-activity-id)
            :activity/procurement-nr "123 R 456"})

    (is (thrown? Exception
                 (tu/local-command tu/mock-user-boss
                                   :activity/delete
                                   {:db/id (tu/get-data :new-activity-id)})))

    (is (not (:meta/deleted? (du/entity (tu/db)
                                        (tu/get-data :new-activity-id)))))

    ;; Now let's remove the procurement number
    (tu/tx [:db/retract (tu/get-data :new-activity-id)
            :activity/procurement-nr "123 R 456"])

    (tu/local-command tu/mock-user-boss
                      :activity/delete
                      {:db/id (tu/get-data :new-activity-id)})

    (is (:meta/deleted? (du/entity (tu/db)
                                   (tu/get-data :new-activity-id))))))
