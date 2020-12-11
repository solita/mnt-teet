(ns ^:db cooperation.cooperation-commands-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.test.utils :as tu]
            [teet.util.date :as dateu]
            teet.task.task-commands
            [teet.util.datomic :as du]))

(t/use-fixtures
  :each
  tu/with-environment
  (tu/with-db))

(deftest test-for-third-party
  (tu/local-login tu/mock-user-boss)
  (let [project-id (:thk.project/id (du/entity (tu/db) (tu/->db-id "p1")))
        third-party-name "party of third"
        third-party {:cooperation.3rd-party/name third-party-name
                     :cooperation.3rd-party/id-code "123"
                     :cooperation.3rd-party/email "party@example.om"
                     :cooperation.3rd-party/phone "2222"}
        third-party-id (tu/local-command :cooperation/create-3rd-party
                                         {:thk.project/id project-id
                                          :third-party third-party})
        valid-date-for-application (-> (du/entity (tu/db) (tu/->db-id "p1-lc1-act2"))
                                       :activity/estimated-start-date
                                       (dateu/inc-days 1))
        invalid-date-for-application (-> (du/entity (tu/db) (tu/->db-id "p1-lc1-act2"))
                                         :activity/estimated-start-date
                                         (dateu/dec-days 1))
        application {:cooperation.application/type :cooperation.application.type/building-permit-draft
                     :cooperation.application/response-type :cooperation.application.response-type/consent
                     :cooperation.application/date valid-date-for-application}
        invalid-date-application (assoc application :cooperation.application/date invalid-date-for-application)
        application-payload {:thk.project/id project-id
                             :cooperation.3rd-party/name third-party-name
                             :application application}
        invalid-third-party-application-payload {:thk.project/id project-id
                                                 :cooperation.3rd-party/name "No such third party exists"
                                                 :application application}
        res (tu/local-command :cooperation/create-application
                              application-payload)]

    (testing "Can create a third party"
      (is (some? third-party-id)))

    (testing "Can create application with valid date"
      (is (some? res)))

    (testing "Can't create application without matching third-party"
      (is (thrown?
            Exception
            (tu/local-command :cooperation/create-application
                              invalid-third-party-application-payload))))

    (testing "Can't create application without matching the creation date with a task"
      (is (thrown?
            Exception
            (tu/local-command :cooperation/create-application
                              {:thk.project/id project-id
                               :cooperation.3rd-party/name third-party-name
                               :application invalid-date-application}))))))
