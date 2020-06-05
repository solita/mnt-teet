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

(def test-project
  {:thk.project/project-name "Test road"
   :thk.project/custom-start-m 370
   :thk.project/custom-end-m 550
   :thk.project/lifecycles
   [{:db/id 26141988462003014
     :thk.lifecycle/estimated-start-date #inst "2021-03-31T21:00:00.000-00:00"
     :thk.lifecycle/estimated-end-date #inst "2021-10-30T21:00:00.000-00:00"
     :thk.lifecycle/type #:db{:id 33042523437924499
                              :ident :thk.lifecycle-type/construction}
     :thk.lifecycle/activities
     [{:db/id 27685702787400519
       :activity/estimated-start-date #inst "2021-03-31T21:00:00.000-00:00"
       :activity/estimated-end-date #inst "2021-10-30T21:00:00.000-00:00"
       :activity/name #:db{:id 46641283250258055
                           :ident :activity.name/construction}
       :activity/manager {:db/id 111
                          :user/given-name "John"
                          :user/family-name "Random"}}]}
    {:db/id 12345
     :thk.lifecycle/estimated-start-date #inst "2020-06-21T21:00:00.000-00:00"
     :thk.lifecycle/estimated-end-date #inst "2020-10-29T22:00:00.000-00:00"
     :thk.lifecycle/type #:db{:id 59945373946347666
                              :ident :thk.lifecycle-type/design}
     :thk.lifecycle/activities
     [{:db/id 12873082138003082
       :activity/estimated-start-date #inst "2020-06-22T00:00:00.000-00:00"
       :activity/estimated-end-date #inst "2020-06-30T00:00:00.000-00:00"
       :activity/name #:db{:id 57416497202462854
                           :ident :activity.name/land-acquisition}
       :activity/status #:db{:id 54491796272580051
                             :ident :activity.status/in-preparation}
       :meta/creator #:db{:id 13154557114712222}
       :meta/created-at #inst "2020-06-04T12:01:27.756-00:00"
       :activity/manager {:db/id 222
                          :user/given-name "Ran"
                          :user/family-name "Johndom"}}
      {:db/id 66753549945537349
       :activity/estimated-start-date #inst "2020-06-21T21:00:00.000-00:00"
       :activity/estimated-end-date #inst "2020-10-29T22:00:00.000-00:00"
       :activity/name #:db{:id 69757415712620677
                           :ident :activity.name/detailed-design}

       :activity/manager {:db/id 222
                          :user/given-name "Ran"
                          :user/family-name "Johndom"}}]}]
   :thk.project/owner
   #:user{:id #uuid "ccbedb7b-ab30-405c-b389-292cdfe85271"
          :given-name "Carla"
          :family-name "Consultant"
          :email "carla.consultant@example.com"}})
