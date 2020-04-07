(ns ^:db teet.system-test
  (:require [clojure.test :refer :all]
            [teet.test.utils :as tu]
            teet.system.system-queries))

(use-fixtures :once tu/with-environment tu/with-db)

(deftest db-query
  (testing ":teet.system/db can be called by anonymous user"
    (is (= (:status (tu/local-query nil :teet.system/db {}))
           200))))
