(ns teet.activity-commands-test
  (:require [clojure.test :refer :all]
            [teet.activity.activity-db :as activity-db]))

(deftest manager-history
  (testing "returns empty seq if there are no managers"
    (is (empty? (activity-db/manager-history [] {}))))

  (testing "returns one element seq if there has been one manager"
    (let [manager-period-start-timestamp #inst "2020-04-06T21:00:00.000-00:00"
          manager {:db/id 111
                   :user/given-name "John"
                   :user/family-name "Random"}]
      (is (= (activity-db/manager-history [{:modified-at manager-period-start-timestamp
                                            :tx 12345
                                            :ref (:db/id manager)}]
                                          {(:db/id manager) manager})
             [{:manager manager
               :period [manager-period-start-timestamp nil]}]))))

  (testing "returns two element when there have been two managers"
    (let [first-manager-period-start-timestamp #inst "2020-04-06T21:00:00.000-00:00"
          manager-change-timestamp #inst "2020-04-07T21:00:00.000-00:00"
          first-manager {:db/id 111
                         :user/given-name "John"
                         :user/family-name "Random"}
          second-manager {:db/id 222
                          :user/given-name "Ran"
                          :user/family-name "Johndom"}]
      (is (= (activity-db/manager-history [{:modified-at first-manager-period-start-timestamp
                                            :tx 12345
                                            :ref (:db/id first-manager)}
                                           {:modified-at manager-change-timestamp
                                            :tx 12346
                                            :ref (:db/id second-manager)}]
                                          {(:db/id first-manager) first-manager
                                           (:db/id second-manager) second-manager})
             [{:manager first-manager
               :period [first-manager-period-start-timestamp manager-change-timestamp]}
              {:manager second-manager
               :period [manager-change-timestamp nil]}])))))
