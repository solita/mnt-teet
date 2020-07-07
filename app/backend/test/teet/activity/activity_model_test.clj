(ns teet.activity.activity-model-test
  (:require [clojure.test :refer :all]
            [teet.activity.activity-model :as activity-model]))

(deftest conflicting-schedules?
  (testing "Activity conflicts with itself"
    (let [activity {:activity/actual-start-date #inst "2020-04-13T21:00:00.000-00:00"
                    :activity/actual-end-date #inst "2020-04-15T21:00:00.000-00:00"}]
      (is (activity-model/conflicting-schedules? activity
                                                 activity))))

  (testing "Activities with partly overlapping schedules conflict"
    (is (activity-model/conflicting-schedules? {:activity/actual-start-date #inst "2020-04-13T21:00:00.000-00:00"
                                                :activity/actual-end-date #inst "2020-04-15T21:00:00.000-00:00"}
                                               {:activity/actual-start-date #inst "2020-04-14T21:00:00.000-00:00"
                                                :activity/actual-end-date #inst "2020-04-16T21:00:00.000-00:00"})))

  (testing "Activities conflict if the schedule of one is included within the schedule of the other"
    (is (activity-model/conflicting-schedules? {:activity/actual-start-date #inst "2020-04-10T21:00:00.000-00:00"
                                                :activity/actual-end-date #inst "2020-04-15T21:00:00.000-00:00"}
                                               {:activity/actual-start-date #inst "2020-04-11T21:00:00.000-00:00"
                                                :activity/actual-end-date #inst "2020-04-13T21:00:00.000-00:00"})))

  (testing "Activities with separate schedules don't conflict with each other"
    (is (not (activity-model/conflicting-schedules? {:activity/actual-start-date #inst "2020-04-10T21:00:00.000-00:00"
                                                     :activity/actual-end-date #inst "2020-04-12T21:00:00.000-00:00"}
                                                    {:activity/actual-start-date #inst "2020-04-13T21:00:00.000-00:00"
                                                     :activity/actual-end-date #inst "2020-04-14T21:00:00.000-00:00"}))))

  (testing "It's ok to have another activity coincide with land acquisition"
    (is (not (activity-model/conflicting-schedules? {:activity/name :activity.name/land-acquisition
                                                     :activity/actual-start-date #inst "2020-04-10T21:00:00.000-00:00"
                                                     :activity/actual-end-date #inst "2020-04-13T21:00:00.000-00:00"}
                                                    {:activity/name :activity.name/detailed-design
                                                     :activity/actual-start-date #inst "2020-04-12T21:00:00.000-00:00"
                                                     :activity/actual-end-date #inst "2020-04-14T21:00:00.000-00:00"})))
    (is (activity-model/conflicting-schedules? {:activity/name :activity.name/land-acquisition
                                                :activity/actual-start-date #inst "2020-04-10T21:00:00.000-00:00"
                                                :activity/actual-end-date #inst "2020-04-13T21:00:00.000-00:00"}
                                               {:activity/name :activity.name/land-acquisition
                                                :activity/actual-start-date #inst "2020-04-12T21:00:00.000-00:00"
                                                :activity/actual-end-date #inst "2020-04-14T21:00:00.000-00:00"})
        "They can't both be land acquisitions")))

(deftest conflicts?
  (testing "There's no conflict if either one of the activities is completed"
    (is (not (activity-model/conflicts? {:activity/status :activity.status/completed
                                         :activity/actual-start-date #inst "2020-04-10T21:00:00.000-00:00"
                                         :activity/actual-end-date #inst "2020-04-13T21:00:00.000-00:00"}
                                        {:activity/status :activity.status/in-preparation
                                         :activity/actual-start-date #inst "2020-04-12T21:00:00.000-00:00"
                                         :activity/actual-end-date #inst "2020-04-14T21:00:00.000-00:00"})))))
