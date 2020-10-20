(ns teet.util.date-test
  (:require [clojure.test :refer :all]
            [teet.util.date :as sut]))

(deftest test-past  
  (is (= nil (sut/test-past))))

