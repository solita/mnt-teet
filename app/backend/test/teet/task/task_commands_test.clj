(ns ^:db teet.task.task-commands-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.test.utils :as tu]
            teet.task.task-commands
            [datomic.client.api :as d]
            [teet.util.datomic :as du])
  (:import (java.util Date)))

(t/use-fixtures
  :each
  (tu/with-config {:file {:allowed-suffixes #{"png" "doc" "xls"}
                          :image-suffixes #{"png" "jpg" "gif"}}})
  tu/with-environment
  tu/with-global-data
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

(deftest delete-regular-task
  (tu/local-login tu/mock-user-boss)
  (let [act-id (tu/->db-id "p1-lc1-act1")
        act (du/entity (tu/db) act-id)]

    (testing "Regular task deletion works"
      (tu/create-task
       {:activity act-id
        :task {:task/group :task.group/land-purchase
               :task/type :task.type/plot-allocation-plan
               :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
               :task/estimated-start-date (:activity/estimated-start-date act)
               :task/estimated-end-date (:activity/estimated-end-date act)}}
       :task-id)
      (is (tu/local-command :task/delete {:db/id (tu/get-data :task-id)})))))

(deftest delete-task-sent-to-thk
  (tu/local-login tu/mock-user-boss)
  (let [act-id (tu/->db-id "p1-lc1-act2")
        act (du/entity (tu/db) act-id)]
    (testing "Send to THK task can only be deleted by admin"
      (tu/create-task
       {:activity act-id
        :task {:task/group :task.group/design
               :task/type :task.type/design-road-safety-audit
               :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
               :task/estimated-start-date (:activity/estimated-start-date act)
               :task/estimated-end-date (:activity/estimated-end-date act)
               :task/send-to-thk? true}}
       :task-id-thk)

      (is (thrown-with-msg?
           Exception #"Pre check failed"
           (tu/local-command :task/delete {:db/id (tu/get-data :task-id-thk)})))

      (testing "Deletion works after giving admin permission"
        (tu/give-admin-permission tu/mock-user-boss)
        (is (tu/local-command :task/delete {:db/id (tu/get-data :task-id-thk)}))
        (is (:meta/deleted? (du/entity (tu/db) (tu/get-data :task-id-thk)))
            "task is marked as deleted")))))

(deftest deleting-task-with-files
  (let [act-id (tu/->db-id "p1-lc1-act1")
        act (du/entity (tu/db) act-id)]
    (tu/local-login tu/mock-user-boss)
    (tu/create-task
     {:activity act-id
      :task {:task/group :task.group/land-purchase
             :task/type :task.type/plot-allocation-plan
             :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
             :task/estimated-start-date (:activity/estimated-start-date act)
             :task/estimated-end-date (:activity/estimated-end-date act)}}
     :task-id)

    (tu/store-data!
     :file
     (tu/fake-upload (tu/get-data :task-id)
                     {:file/name "image.png"
                      :file/size 666}))

    (testing "Deleting task with file fails"
      (tu/is-thrown-with-data?
       {:teet/error :task-has-files}
       (tu/local-command :task/delete {:db/id (tu/get-data :task-id)})))

    (testing "Deleting file before task works"
      (tu/local-command :file/delete
                        {:file-id (:db/id (tu/get-data :file))})

      (is (tu/local-command :task/delete {:db/id (tu/get-data :task-id)})))))
