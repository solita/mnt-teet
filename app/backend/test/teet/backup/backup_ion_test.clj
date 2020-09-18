(ns teet.backup.backup-ion-test
  (:require [teet.backup.backup-ion :as backup-ion]
            [teet.test.utils :as tu]
            [clojure.test :refer [use-fixtures deftest is testing]]
            [clojure.java.io :as io]
            [datomic.client.api :as d]
            [clojure.walk :as walk]))

(use-fixtures :each tu/with-environment)

(defn- remove-db-ids [tree]
  (walk/prewalk (fn [x]
                  (if (map? x)
                    (dissoc x :db/id)
                    x))
                tree))

(deftest restore-tx-backup
  (let [with-populated-db (tu/with-db)
        with-empty-db (tu/with-db {:migrate? false
                                   :mock-users? false
                                   :data-fixtures []})
        project (atom nil)
        backup-file (java.io.File/createTempFile "testbackup" ".zip")
        get-project #(d/pull (tu/db) '[*] [:thk.project/id "11111"])]
    (testing "Backup all transactions from a populated db"
      (with-populated-db
        (fn []
          (reset! project (get-project))
          (backup-ion/output-all-tx (tu/connection)
                                    (io/output-stream backup-file)))))

    (testing "Restore to new empty database"
      (with-empty-db
        (fn []
          (is (empty? (d/q '[:find ?p :where [?p :db/ident :thk.project/id]]
                           (tu/db)))
              "Database is empty before restore")
          (backup-ion/restore-tx-file {:conn (tu/connection)
                                       :file backup-file})
          (let [restored-project (get-project)]
            (is (= (remove-db-ids restored-project)
                   (remove-db-ids @project))
                "Project is the same (expect :db/id attrs)")
            (is (integer? (:db/id restored-project)))
            (is (integer? (:db/id @project)))
            (is (not= (:db/id restored-project)
                      (:db/id @project)))))))))
