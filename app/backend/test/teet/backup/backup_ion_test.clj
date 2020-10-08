(ns teet.backup.backup-ion-test
  (:require [teet.backup.backup-ion :as backup-ion]
            [teet.test.utils :as tu :refer [tx]]
            [clojure.test :refer [use-fixtures deftest is testing]]
            [clojure.java.io :as io]
            [datomic.client.api :as d]
            [clojure.walk :as walk]))

(use-fixtures :each tu/with-environment)

(defn- remove-keys [tree & keys]
  (walk/prewalk (fn [x]
                  (if (map? x)
                    (apply dissoc x keys)
                    x))
                tree))

(defn- remove-db-ids [tree]
  (remove-keys tree :db/id))

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

          ;; Fetch project
          (reset! project (get-project))

          ;; Insert file and user seen (tuple attr)
          (let [file-id (get-in (tx {:db/id "testfile" :file/name "testfile"})
                                [:tempids "testfile"])]
            (tx {:db/id "seen-by-manager"
                 :file-seen/file file-id
                 :file-seen/user [:user/id tu/manager-id]}))

          (backup-ion/output-all-tx (tu/connection)
                                    (io/output-stream backup-file)))))

    ;; TODO: Test backup/restore of a tupleattrs with ref that isn't
    ;; included in the backup
    (testing "Restore to new empty database"
      (with-empty-db
        (fn []
          (is (empty? (d/q '[:find ?p :where [?p :db/ident :thk.project/id]]
                           (tu/db)))
              "Database is empty before restore")
          (backup-ion/restore-tx-file {:conn (tu/connection)
                                       :file backup-file})
          (testing "Restored project matches old"
            (let [restored-project (get-project)]
              (is (= (remove-db-ids restored-project)
                     (remove-db-ids @project))
                  "Project is the same (expect :db/id attrs)")
              (is (integer? (:db/id restored-project)))
              (is (integer? (:db/id @project)))
              (is (not= (:db/id restored-project)
                        (:db/id @project)))))

          (testing "File seen has correct tuple"
            (let [file-with-seen
                  (ffirst
                   (d/q '[:find (pull ?f
                                      [:db/id
                                       :file/name
                                       {:file-seen/_file
                                        [:file-seen/file+user
                                         {:file-seen/user [:db/id :user/given-name :user/family-name]}]}])
                          :where [?f :file/name "testfile"]]
                        (tu/db)))

                  user-id (get-in file-with-seen [:file-seen/_file 0 :file-seen/user :db/id])
                  file-id (:db/id file-with-seen)]
              (is (= (remove-keys file-with-seen :db/id :file-seen/file+user)
                     {:file/name "testfile"
                      :file-seen/_file
                      [{:file-seen/user {:user/given-name "Danny D."
                                         :user/family-name "Manager"}}]}))
              (is (= (get-in file-with-seen [:file-seen/_file 0 :file-seen/file+user])
                     [file-id user-id])))))))))
