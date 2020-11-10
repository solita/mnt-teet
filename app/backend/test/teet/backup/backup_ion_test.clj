(ns teet.backup.backup-ion-test
  (:require [teet.backup.backup-ion :as backup-ion]
            [teet.test.utils :as tu :refer [tx]]
            [clojure.test :refer [use-fixtures deftest is testing]]
            [clojure.java.io :as io]
            [datomic.client.api :as d]
            [clojure.walk :as walk]
            [teet.util.datomic :as du]))

(use-fixtures :each tu/with-environment tu/with-global-data)

(defn- remove-keys [tree & keys]
  (walk/prewalk (fn [x]
                  (if (map? x)
                    (apply dissoc x keys)
                    x))
                tree))

(defn- remove-db-ids [tree]
  (remove-keys tree :db/id))

(defn get-project []
  (d/pull (tu/db)
          '[*
            {:thk.project/lifecycles
             [*
              {:thk.lifecycle/activities
               [*
                {:activity/tasks
                 [*
                  {:task/comments [*]}]}]}]}]
          [:thk.project/id "11111"]))

(deftest restore-tx-backup
  (let [with-populated-db (tu/with-db)
        with-empty-db (tu/with-db {:migrate? false
                                   :mock-users? false
                                   :data-fixtures []})
        project (atom nil)
        backup-file (java.io.File/createTempFile "testbackup" ".zip")
        test-file-name (str (gensym "testfile"))
        pull-seen-by-manager-tx (fn []
                                  (first
                                   (d/q '[:find (pull ?tx [:db/txInstant :tx/author])
                                          :in $ ?name ?manager-id
                                          :where
                                          [?file :file/name ?name]
                                          [?e :file-seen/file ?file]
                                          [?e :file-seen/seen-at _ ?tx true]]
                                        (tu/db)
                                        test-file-name
                                        tu/manager-id)))]
    (testing "Backup all transactions from a populated db"
      (with-populated-db
        (fn []

          ;; Insert file and user seen (tuple attr)
          (let [file-id (get-in (tx {:db/id "testfile"
                                     :file/name test-file-name})
                                [:tempids "testfile"])
                ;; First mark seen at to start of epoch
                tx (tx {:db/id "seen-by-manager"
                        :file-seen/file file-id
                        :file-seen/user [:user/id tu/manager-id]
                        :file-seen/seen-at (java.util.Date. 0)})
                seen-by-id (get-in tx [:tempids "seen-by-manager"])]


            (tu/store-data! :seen-by-manager-at
                            (ffirst (d/q '[:find (max ?d)
                                           :where
                                           [_ :file-seen/seen-at _ ?tx]
                                           [?tx :db/txInstant ?d]]
                                         (tu/db))))

            ;; Update mark seen to current time
            (tx {:db/id seen-by-id
                 :file-seen/seen-at (java.util.Date.)})

            ;; Store tx info for latest tx
            (tu/store-data! :seen-by-manager-tx
                            (pull-seen-by-manager-tx)))

          ;; Create a task and comment on it
          (tu/local-login tu/mock-user-boss)
          (let [activity-id (tu/->db-id "p1-lc1-act1")
                {:activity/keys [estimated-start-date estimated-end-date]}
                (du/entity (tu/db) activity-id)
                task-id (tu/create-task
                         {:activity activity-id
                          :task {:task/group :task.group/land-purchase
                                 :task/type :task.type/plot-allocation-plan
                                 :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                                 :task/estimated-start-date estimated-start-date
                                 :task/estimated-end-date estimated-end-date}})]
            (tu/local-command :comment/create
                              {:entity-id task-id
                               :entity-type :task
                               :comment "comment should survive backup/restore"
                               :visibility :comment.visibility/all}))

          ;; Save project state before backup
          (reset! project (get-project))

          (#'backup-ion/output-all-tx (tu/connection)
                                      (io/output-stream backup-file)))))


    (testing "Backup file has been created"
      (is (.canRead backup-file) "Backup file exists")
      (is (> (.length backup-file) 10240) "It is over 10k in length"))

    (testing "Restore to new empty database"
      (with-empty-db
        (fn []
          (is (empty? (d/q '[:find ?p :where [?p :db/ident :thk.project/id]]
                           (tu/db)))
              "Database is empty before restore")
          (#'backup-ion/restore-tx-file {:conn (tu/connection)
                                         :file backup-file})
          (testing "Restored project matches old"
            (let [restored-project (get-project)]
              (is (= (remove-db-ids restored-project)
                     (remove-db-ids @project))
                  "Project is the same (expect :db/id attrs)")
              (is (integer? (:db/id restored-project)))
              (is (integer? (:db/id @project)))))

          (testing "File seen has correct tuple"
            (let [file-with-seen
                  (ffirst
                   (d/q '[:find (pull ?f
                                      [:db/id
                                       :file/name
                                       {:file-seen/_file
                                        [:file-seen/file+user
                                         {:file-seen/user [:db/id :user/given-name :user/family-name]}]}])
                          :where [?f :file/name ?name]
                          :in $ ?name]
                        (tu/db)
                        test-file-name))

                  user-id (get-in file-with-seen [:file-seen/_file 0 :file-seen/user :db/id])
                  file-id (:db/id file-with-seen)]
              (is (= (remove-keys file-with-seen :db/id :file-seen/file+user)
                     {:file/name test-file-name
                      :file-seen/_file
                      [{:file-seen/user {:user/given-name "Danny D."
                                         :user/family-name "Manager"}}]}))
              (is (= (get-in file-with-seen [:file-seen/_file 0 :file-seen/file+user])
                     [file-id user-id]))))

          (testing "File seen tx data is correct"
            (let [old-file-seen-tx (tu/get-data :seen-by-manager-tx)
                  restored-file-seen-tx (pull-seen-by-manager-tx)]
              (is (= old-file-seen-tx restored-file-seen-tx))

              (testing "Previous state is also present"
                (let [db* (d/as-of (tu/db)
                                   (java.util.Date.
                                    (inc (.getTime (tu/get-data :seen-by-manager-at)))))
                      seen (ffirst
                            (d/q '[:find (pull ?e [:file-seen/seen-at])
                                   :where
                                   [?file :file/name ?name]
                                   [?e :file-seen/file ?file]
                                   :in $ ?name]
                                 db* test-file-name))]
                  (is (= seen {:file-seen/seen-at (java.util.Date. 0)})))))))))))
