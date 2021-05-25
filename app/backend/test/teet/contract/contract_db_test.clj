(ns ^:db teet.contract.contract-db-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [teet.test.utils :as tu]
            [teet.contract.contract-db :as contract-db]
            [teet.util.date :as dateu]
            [teet.contract.contracts-queries :as contracts-queries]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(def contract-txes
  [{:db/id "signed-contract"
    :thk.contract/procurement-id "123"
    :thk.contract/procurement-part-id "12"}

   {:db/id "signed-contract2"
    :thk.contract/procurement-id "22"
    :thk.contract/procurement-part-id "33"
    :thk.contract/start-of-work (dateu/inc-days (dateu/now) 5)}

   {:db/id "in-progress-contract1"
    :thk.contract/procurement-id "123"
    :thk.contract/procurement-part-id "123"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 5)
    :thk.contract/deadline (dateu/inc-days (dateu/now) 31)}

   {:db/id "in-progress-contract2"
    :thk.contract/procurement-id "111"
    :thk.contract/procurement-part-id "222"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 5)
    :thk.contract/deadline (dateu/inc-days (dateu/now) 15)
    :thk.contract/extended-deadline (dateu/inc-days (dateu/now) 32)}

   {:db/id "in-progress-contract3"
    :thk.contract/procurement-id "33"
    :thk.contract/procurement-part-id "44"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 5)}

   {:db/id "deadline-approaching"
    :thk.contract/procurement-id "11"
    :thk.contract/procurement-part-id "22"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 5)
    :thk.contract/deadline (dateu/inc-days (dateu/now) 15)}

   {:db/id "deadline-overdue-contract"
    :thk.contract/procurement-id "1111"
    :thk.contract/procurement-part-id "2222"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 5)
    :thk.contract/deadline (dateu/inc-days (dateu/now) 15)
    :thk.contract/extended-deadline (dateu/dec-days (dateu/now) 1)}

   {:db/id "deadline-overdue-contract2"
    :thk.contract/procurement-id "11111"
    :thk.contract/procurement-part-id "22222"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 5)
    :thk.contract/deadline (dateu/dec-days (dateu/now) 1)}

   {:db/id "warranty-contract"
    :thk.contract/procurement-id "2"
    :thk.contract/procurement-part-id "3"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 5)
    :thk.contract/deadline (dateu/dec-days (dateu/now) 1)
    :thk.contract/warranty-end-date (dateu/inc-days (dateu/now) 15)}

   {:db/id "warranty-contract2"
    :thk.contract/procurement-id "3"
    :thk.contract/procurement-part-id "4"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 5)
    :thk.contract/deadline (dateu/dec-days (dateu/now) 1)
    :thk.contract/extended-deadline (dateu/dec-days (dateu/now) 1)
    :thk.contract/warranty-end-date (dateu/inc-days (dateu/now) 15)}

   {:db/id "completed"
    :thk.contract/procurement-id "4"
    :thk.contract/procurement-part-id "5"
    :thk.contract/start-of-work (dateu/dec-days (dateu/now) 15)
    :thk.contract/deadline (dateu/dec-days (dateu/now) 10)
    :thk.contract/extended-deadline (dateu/dec-days (dateu/now) 4)
    :thk.contract/warranty-end-date (dateu/dec-days (dateu/now) 2)}])

(deftest contract-status-test
  (testing "Contract statuses"
    (apply tu/tx contract-txes)
    (testing "By default the contract status is :thk.contract.status/signed"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["123" "12"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/signed))))

    (testing "If contract start of work in the future the contract status is :thk.contract.status/signed"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["22" "33"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/signed))))

    (testing "If contract has no deadlines and start of work passed the contract status is :thk.contract.status/in-progress"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["33" "44"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/in-progress))))

    (testing "After start of work date and time until deadline is more than 30 days status is in-progress"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["123" "123"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/in-progress))))
    (testing "After start of work date and time until extended deadline is more than 30 days status is in-progress"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["111" "222"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/in-progress))))
    (testing "Time until deadline is less than 30 days the status is deadline-approaching"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["11" "22"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/deadline-approaching))))

    (testing "Deadline is in the past so contract is in status deadline overdue"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["11111" "22222"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/deadline-overdue))))

    (testing "Extended deadline is in the past so contract is in status deadline overdue"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["1111" "2222"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/deadline-overdue))))

    (testing "Contract is in warranty when deadline has passed"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["2" "3"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/warranty))))

    (testing "Contract is in warranty when extended deadline has passed"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["3" "4"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/warranty))))

    (testing "Contract is completed when both deadlines and warranty end date has passed"
      (let [contract (contract-db/get-contract
                       (tu/db)
                       [:thk.contract/procurement-id+procurement-part-id ["4" "5"]])]
        (is (= (:thk.contract/status contract)
               :thk.contract.status/completed))))

    (testing "All contracts get statuses"
      (let [contracts (contracts-queries/contract-listing-query
                       (tu/db)
                       tu/mock-user-boss
                       {})]
        (is (= (count contracts)
               (count contract-txes)))

        (is (every? :thk.contract/status contracts))))))
