(ns teet.meeting.meeting-commands-test
  (:require [teet.meeting.meeting-commands :as meeting-commands]
            [teet.test.utils :as tu]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(defn test-meeting []
  (let [now (System/currentTimeMillis)]
    {:meeting/title "test meeting"
     :meeting/location "inside the test runner"
     :meeting/start (java.util.Date. (+ now (* 1000 60)))
     :meeting/end (java.util.Date. (+ now (* 1000 60 60)))
     :meeting/organizer tu/mock-user-manager}))

(deftest create-meeting
  (tu/local-login tu/mock-user-boss)
  (testing "Invalid meeting isn't created"
    (is (= "Spec validation failed"
           (:body (tu/local-command :meeting/create
                                    {:activity-eid (tu/->db-id "p1-lc1-act1")
                                     :meeting/form-data {:meeting/title "foo"}})))))

  (testing "Valid meeting is created"

    (let [response
          (tu/local-command :meeting/create
                            {:activity-eid (tu/->db-id "p1-lc1-act1")
                             :meeting/form-data (test-meeting)})]
      (is (get-in response [:tempids "new-meeting"])
          "meeting created successfully"))))
