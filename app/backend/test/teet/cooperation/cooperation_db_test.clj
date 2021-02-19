(ns ^:db teet.cooperation.cooperation-db-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [teet.test.utils :as tu]
            [teet.util.datomic :as du]
            [teet.util.date :as dateu]
            [clojure.tools.logging :as log]
            [teet.cooperation.cooperation-db :as cooperation-db]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(def third-party-name "Test company")

(defn get-project-id [db] (:thk.project/id (du/entity db (tu/->db-id "p2"))))

(defn create-task [db]
  (let [activity-id (tu/->db-id "p2-lc1-act1")
        activity-entity (du/entity db activity-id)
        task-id (tu/create-task
                  {:activity activity-id
                   :task {:task/group :task.group/construction
                          :task/type :task.type/collaboration
                          :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                          :task/estimated-start-date (:activity/estimated-start-date activity-entity)
                          :task/estimated-end-date (:activity/estimated-end-date activity-entity)}})]
    task-id))

(defn complete-task [user task-id]
  (let [ret-value (tu/complete-task {:user user :task-id task-id})]
    ret-value))

(defn create-3rd-party
  [project-id third-party-name]
  (let [third-party {:db/id "new-third-party"
                     :cooperation.3rd-party/name third-party-name
                     :cooperation.3rd-party/id-code "1111"
                     :cooperation.3rd-party/email "party@example.om"
                     :cooperation.3rd-party/phone "2222"}]
    (get-in (tu/local-command :cooperation/save-3rd-party
              {:thk.project/id project-id
               :third-party third-party})
      [:tempids "new-third-party"])))

(defn get-application [db]
  (let [valid-date
        (-> (du/entity db (tu/->db-id "p2-lc1-act1"))
          :activity/estimated-start-date (dateu/inc-days 1))]
    {:cooperation.application/type :cooperation.application.type/building-permit-draft
     :cooperation.application/response-type :cooperation.application.response-type/consent
     :cooperation.application/date valid-date}))

(defn create-application [db third-party-teet-id]
  (let [application-payload {:thk.project/id (get-project-id db)
                             :third-party-teet-id third-party-teet-id
                             :application (get-application db)}]
    (tu/local-command :cooperation/create-application
      application-payload)))

(defn create-response [project-id application-id]
  (tu/local-command :cooperation/save-application-response
    {:thk.project/id project-id
     :application-id application-id
     :form-data {:cooperation.response/status :cooperation.response.status/no-objection
                 :cooperation.response/date (dateu/now)
                 :cooperation.response/valid-months 12}}))

(deftest third-party-application-task-test
  (tu/local-login tu/mock-user-boss)
  (let [third-party-id (create-3rd-party (get-project-id (tu/db)) third-party-name)
        third-party-teet-id (:teet/id (du/entity (tu/db) third-party-id))
        application-res (create-application (tu/db) third-party-teet-id)
        new-application-id (get-in application-res [:tempids "new-application"])]
    (create-response (get-project-id (tu/db)) new-application-id)
    (testing ;; test there is a task attached to activity of the new application
      (let [no-task (cooperation-db/third-party-application-task (tu/db) third-party-id new-application-id)
            _ (create-task (tu/db))
            collaboration-task (cooperation-db/third-party-application-task (tu/db) third-party-id new-application-id)
            _ (complete-task tu/mock-user-boss (:db/id collaboration-task))
            error-message (cooperation-db/third-party-application-task (tu/db) third-party-id new-application-id)]
        ;; no task returned initially
        (is (nil? no-task))
        ;; collaboration task is returned then added
        (is (= (:task/description collaboration-task) "Design requirements for testing."))
        ;; error message returned then for already submitted task
        (is (= (:error-message error-message) "Task can not be submitted"))))))


