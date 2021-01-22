(ns ^:db teet.system-test
  (:require [clojure.test :refer :all]
            [teet.test.utils :as tu]
            teet.system.system-queries
            teet.environment))

(use-fixtures :once tu/with-environment (tu/with-db))

(deftest db-query
  (let [migrated-atom @#'teet.environment/asset-db-migrated?]
    (is (not @migrated-atom) "asset db not migrated before")
    (testing ":teet.system/db can be called by anonymous user"
      (is (= (:status (tu/local-query nil :teet.system/db {}))
             200))
      (is @migrated-atom "Asset db was initialized by system query"))))
