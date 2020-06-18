(ns ^:db teet.project.project-commands-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.test.utils :as tu]
            [datomic.client.api :as d]))

(t/use-fixtures :each
  tu/with-environment
  (tu/with-db))

;; PENDING: No tests at the moment (pm test was moved to activity command tests)
