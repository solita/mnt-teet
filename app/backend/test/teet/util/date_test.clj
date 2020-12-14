(ns teet.util.date-test
  (:require [clojure.test :refer :all]
            [teet.util.date :as sut]))

(deftest test-past
  (is (= nil (sut/test-past))))

(deftest date-within?
  (testing "Date is within an interval from itself to itself"
    (let [test-date (java.util.Date.)]
      (is (sut/date-within? test-date [test-date test-date]))))

  (testing "Dates before and after the interval are not within, but interval limits are"
    (let [before (java.util.Date. 2020 11 1)
          start  (java.util.Date. 2020 11 2)
          end    (java.util.Date. 2020 11 3)
          after  (java.util.Date. 2020 11 4)]
      (is (not (sut/date-within? before [start end])))
      (is (not (sut/date-within? after [start end])))
      (is (sut/date-within? start [start end]))
      (is (sut/date-within? end [start end])))))
