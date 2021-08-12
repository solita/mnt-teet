(ns ^:db teet.contract.contract-authorization-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.test.utils :as tu]
            [teet.authorization.authorization-db :as authorization-db]
            [teet.authorization.authorization-core :as authorization]))

(t/use-fixtures
  :each
  tu/with-environment
  (tu/with-db)
  tu/with-global-data)

(defn contract-authorization-txes
  []
  [{:db/id "contract1"
    :thk.contract/procurement-id "1"
    :thk.contract/procurement-part-id "1"
    :thk.contract/targets [(tu/->db-id "p1-lc1-act1")]}
   {:company/name "Company for a test"
    :company/business-registry-code "EE123123"
    :company/country :ee
    :db/id "company1"}
   {:company/name "Company for a test2"
    :company/business-registry-code "EE1231234"
    :company/country :ee
    :db/id "company2"}
   {:company-contract-employee/role :company-contract-employee.role/company-project-manager
    :company-contract-employee/active? true
    :company-contract-employee/user tu/mock-user-carla-consultant
    :db/id "employee1"}
   {:company-contract/contract "contract1"
    :company-contract/company "company1"
    :company-contract/employees ["employee1"]}
   {:activity/tasks [{:db/id "new-task"
                      :thk.activity/id "111"
                      :task/type :task.type/owners-supervision}]
    :db/id (tu/->db-id "p1-lc1-act1")}


   {:company-contract-employee/role :company-contract-employee.role/company-representative
    :company-contract-employee/user tu/mock-user-edna-consultant
    :company-contract-employee/active? true
    :db/id "employee2"}
   {:company-contract/contract "contract2"
    :company-contract/company "company2"
    :company-contract/employees ["employee2"]}
   {:activity/tasks [{:db/id "new-task2"
                      :thk.activity/id "222"
                      :task/type :task.type/owners-supervision}]
    :db/id (tu/->db-id "p1-lc1-act2")}
   {:db/id "contract2"
    :thk.contract/procurement-id "2"
    :thk.contract/procurement-part-id "2"
    :thk.contract/targets ["new-task2"]}])


(deftest contract-authorization-test

  (testing "Contract employees get authorized by contract"
    (apply tu/tx (contract-authorization-txes))
    (testing "Carla gets company-project-manager role for activity p1-lc1-act1"
      (is (= (authorization-db/user-roles-for-target
               (tu/db)
               tu/mock-user-carla-consultant
               (tu/->db-id "p1-lc1-act1"))
             #{:company-contract-employee.role/company-project-manager})))
    (testing "User should also get the same roles for the tasks if the target is an activity"
      (is (= (authorization-db/user-roles-for-target
               (tu/db)
               tu/mock-user-carla-consultant
               (:db/id (tu/entity [:thk.activity/id "111"])))
             #{:company-contract-employee.role/company-project-manager})))

    (testing "A contract for a task under an activity should not grant roles for the activity"
      (is (= (authorization-db/user-roles-for-target
               (tu/db)
               tu/mock-user-edna-consultant
               (tu/->db-id "p1-lc1-act2"))
             #{})))

    (testing "Contract to a task grants access to the task"
      (is (= (authorization-db/user-roles-for-target
               (tu/db)
               tu/mock-user-edna-consultant
               (:db/id (tu/entity [:thk.activity/id "222"])))
             #{:company-contract-employee.role/company-representative})))

    (testing "Contract roles can be fetched for companies as well"
      (is (= (authorization-db/user-roles-for-company
               (tu/db)
               tu/mock-user-edna-consultant
               [:company/business-registry-code "EE1231234"])
             #{:company-contract-employee.role/company-representative})))

    (testing "Contract under a project grants read access to the project"
      (is (true? (authorization/general-project-access?
               (tu/db) tu/mock-user-edna-consultant
               (tu/->db-id "p1")))))))
