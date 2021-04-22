(ns ^:db teet.system-test
  (:require [clojure.test :refer [use-fixtures deftest testing is]]
            [teet.test.utils :as tu]
            teet.system.system-queries
            teet.environment))

(use-fixtures :once
  tu/with-environment
  (tu/with-config {:enabled-features #{:asset-db}})
  (tu/with-db))

(deftest db-query
  (testing ":teet.system/db can be called by anonymous user"
    (let [resp (tu/local-query nil :teet.system/db {})]
      (is (= (:status resp) 200)))))
