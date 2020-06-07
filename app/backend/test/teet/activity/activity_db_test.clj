(ns teet.activity-commands-test
  (:require [clojure.test :refer :all]
            [teet.activity.activity-db :as activity-db]))

(deftest manager-histories-by-activity
  (testing "returns empty map if there are no managers in entire project"
    (is (empty? (activity-db/manager-histories-by-activity [] {}))))

  (testing "returns one element seq for activity if there has been one manager"
    (let [manager-period-start-timestamp #inst "2020-04-06T21:00:00.000-00:00"
          manager {:db/id 111
                   :user/given-name "John"
                   :user/family-name "Random"}]
      (is (= (activity-db/manager-histories-by-activity [{:activity 999
                                                          :modified-at manager-period-start-timestamp
                                                          :tx 12345
                                                          :ref (:db/id manager)}]
                                                        {(:db/id manager) manager})
             {999 [{:manager manager
                    :period [manager-period-start-timestamp nil]}]}))))

  (testing "returns two elements for activity  when there have been two managers"
    (let [first-manager-period-start-timestamp #inst "2020-04-06T21:00:00.000-00:00"
          manager-change-timestamp #inst "2020-04-07T21:00:00.000-00:00"
          first-manager {:db/id 111
                         :user/given-name "John"
                         :user/family-name "Random"}
          second-manager {:db/id 222
                          :user/given-name "Ran"
                          :user/family-name "Johndom"}]
      (is (= (activity-db/manager-histories-by-activity [{:activity 999
                                                          :modified-at first-manager-period-start-timestamp
                                                          :tx 12345
                                                          :ref (:db/id first-manager)}
                                                         {:activity 999
                                                          :modified-at manager-change-timestamp
                                                          :tx 12346
                                                          :ref (:db/id second-manager)}]
                                                        {(:db/id first-manager) first-manager
                                                         (:db/id second-manager) second-manager})
             {999 [{:manager first-manager
                    :period [first-manager-period-start-timestamp manager-change-timestamp]}
                   {:manager second-manager
                    :period [manager-change-timestamp nil]}]}))))
  (testing "creates separate timelines for separate activities"
    (let [first-manager-period-start-timestamp #inst "2020-04-06T21:00:00.000-00:00"
          manager-change-timestamp #inst "2020-04-07T21:00:00.000-00:00"
          first-manager {:db/id 111
                         :user/given-name "John"
                         :user/family-name "Random"}
          second-manager {:db/id 222
                          :user/given-name "Ran"
                          :user/family-name "Johndom"}]
      (is (= (activity-db/manager-histories-by-activity [{:activity 888
                                                          :modified-at first-manager-period-start-timestamp
                                                          :tx 12345
                                                          :ref (:db/id first-manager)}
                                                         {:activity 999
                                                          :modified-at manager-change-timestamp
                                                          :tx 12346
                                                          :ref (:db/id second-manager)}]
                                                        {(:db/id first-manager) first-manager
                                                         (:db/id second-manager) second-manager})
             {888 [{:manager first-manager
                    :period [first-manager-period-start-timestamp nil]}]
              999 [{:manager second-manager
                    :period [manager-change-timestamp nil]}]})))))

(def test-project
  {:thk.project/project-name "Test road"
   :thk.project/lifecycles
   [{:db/id 26141988462003014
     :thk.lifecycle/activities
     [{:db/id 27685702787400519
       :activity/manager {:db/id 111
                          :user/given-name "John"
                          :user/family-name "Random"}}]}
    {:db/id 12345
     :thk.lifecycle/type #:db{:id 59945373946347666
                              :ident :thk.lifecycle-type/design}
     :thk.lifecycle/activities
     [{:db/id 12873082138003082
       :activity/manager {:db/id 222
                          :user/given-name "Ran"
                          :user/family-name "Johndom"}}
      {:db/id 66753549945537349
       :activity/manager {:db/id 222
                          :user/given-name "Ran"
                          :user/family-name "Johndom"}}]}]
   :thk.project/owner
   #:user{:id #uuid "ccbedb7b-ab30-405c-b389-292cdfe85271"
          :given-name "Carla"
          :family-name "Consultant"
          :email "carla.consultant@example.com"}})

(deftest project-with-manager-histories
  (testing "sanity check for empty data"
    (is (= (activity-db/project-with-manager-histories {} {})
           {})))

  (testing "if there is no history, adds empty vector"
    (is (= (activity-db/project-with-manager-histories test-project {})
           (activity-db/update-activities test-project
                                          (fn [a]
                                            (assoc a :activity/manager-history []))))))

  (testing "otherwise adds the contents of the history"
    (is (= (activity-db/project-with-manager-histories test-project {12873082138003082 :history})
           (activity-db/update-activities test-project
                                          (fn [a]
                                            (assoc a :activity/manager-history
                                                   (if (= (:db/id a) 12873082138003082)
                                                     :history
                                                     []))))))))
