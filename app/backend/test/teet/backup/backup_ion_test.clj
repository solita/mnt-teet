(ns teet.backup.backup-ion-test
  (:require [teet.backup.backup-ion :as backup-ion]
            [teet.test.utils :as tu]
            [clojure.test :refer [use-fixtures deftest is testing]]))

(use-fixtures :each
  tu/with-environment
  (tu/with-db {:migrate? false
               :mock-users? false
               :data-fixtures []}))

(deftest restore-tx-backup
  (let [ctx
        (backup-ion/restore-tx-file {:file "backup-tx.edn.zip"
                                     :conn (tu/connection)})]
    (def *ctx ctx)
    (is ctx)))
