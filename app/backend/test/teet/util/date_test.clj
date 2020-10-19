(ns teet.util.date-test
  (:require [clojure.test :refer :all]
            [teet.util.date :as sut]))

(deftest "date past functions work"
  (= nil (sut/test-past)))

