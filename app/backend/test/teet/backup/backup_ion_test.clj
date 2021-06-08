(ns ^:db teet.backup.backup-ion-test
  (:require [teet.backup.backup-ion :as backup-ion]
            [teet.test.utils :as tu :refer [tx]]
            [clojure.test :refer [use-fixtures deftest is testing]]
            [clojure.java.io :as io]
            [datomic.client.api :as d]
            [clojure.walk :as walk]
            [teet.util.datomic :as du]
            [teet.environment :as environment]))

(use-fixtures :each
  tu/with-environment
  tu/with-global-data
  (tu/with-config {:enabled-features #{:asset-db}}))

(defn- remove-keys [tree & keys]
  (walk/prewalk (fn [x]
                  (if (map? x)
                    (apply dissoc x keys)
                    x))
                tree))

(defn- remove-db-ids [tree]
  (remove-keys tree :db/id))

(defn- cardinality-many-to-sets [db form]
  (let [card-many-attrs (into #{}
                              (map first)
                              (d/q '[:find ?ident
                                     :where
                                     [?e :db/cardinality :db.cardinality/many]
                                     [?e :db/ident ?ident]]
                                   db))]
    (walk/prewalk (fn [x]
                    (if (and (map-entry? x)
                             (card-many-attrs (first x)))
                      [(first x) (set (second x))]
                      x))
                  form)))

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

;; Define different tests as functions that do initial setup before the backup
;; then return a function to verify the results after the backup

(defn file-user-seen []
  ;; Insert file and user seen (tuple attr)
  (let [test-file-name (str (gensym "testfile"))
        file-id (get-in (tx {:db/id "testfile"
                             :file/name test-file-name})
                        [:tempids "testfile"])
        ;; First mark seen at to start of epoch
        tx (tx {:db/id "seen-by-manager"
                :file-seen/file file-id
                :file-seen/user [:user/id tu/manager-id]
                :file-seen/seen-at (java.util.Date. 0)})
        seen-by-id (get-in tx [:tempids "seen-by-manager"])
        pull-seen-by-manager-tx (fn []
                                  (first
                                   (d/q '[:find (pull ?tx [:db/txInstant :tx/author])
                                          :in $ ?name ?manager
                                          :where
                                          [?file :file/name ?name]
                                          [?e :file-seen/file ?file]
                                          [?e :file-seen/user ?manager]
                                          [?e :file-seen/seen-at _ ?tx true]]
                                        (tu/db)
                                        test-file-name
                                        [:user/id tu/manager-id])))]
    (tu/store-data! :seen-by-manager-at
                    (ffirst (d/q '[:find (max ?d)
                                   :where
                                   [_ :file-seen/seen-at _ ?tx]
                                   [?tx :db/txInstant ?d]]
                                 (tu/db))))

    (Thread/sleep 5) ; make sure next transaction is some time later

    ;; Update mark seen to current time
    (tx {:db/id seen-by-id
         :file-seen/seen-at (java.util.Date.)})

    ;; Store tx info for latest tx
    (tu/store-data! :seen-by-manager-tx
                    (pull-seen-by-manager-tx))

    ;; Return function to verify the data
    (fn []
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
              (is (= seen {:file-seen/seen-at (java.util.Date. 0)})))))))))

(defn task-and-comment []
  ;; Create a task and comment on it
  (tu/local-login tu/mock-user-boss)
  (let [txt "comment should survive backup/restore"
        activity-id (tu/->db-id "p1-lc1-act1")
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
                       :comment txt
                       :visibility :comment.visibility/all})
    ;; Return function to check comment
    ;; Most of it is checked with the project pull
    #(is (seq (d/q '[:find ?e
                     :where [?e :comment/comment ?txt]
                     :in $ ?txt]
                   (tu/db) txt))
         "comment with correct text is found")))

(defn compare-project-pull []
  (let [project (atom nil)]
    ;; Save project state before backup
    (reset! project (get-project))

    ;; Return function to verify it after restore
    (fn []
      (testing "Restored project matches old"
        (let [restored-project (get-project)
              db (tu/db)
              prepare-for-compare (fn [x]
                                    (cardinality-many-to-sets db (remove-db-ids x)))]
          (is (= (prepare-for-compare restored-project)
                 (prepare-for-compare @project))
              "Project is the same (expect :db/id attrs)")
          (is (integer? (:db/id restored-project)))
          (is (integer? (:db/id @project))))))))

(defn asset-db-schema []
  (if-not (environment/feature-enabled? :asset-db)
    :ignore
    (let [list-items #(into #{}
                            (map first)
                            (d/q '[:find ?id
                                   :where
                                   [?e :enum/attribute _]
                                   [?e :db/ident ?id]]
                                 (tu/asset-db)))
          items-before (list-items)]
      (is (seq items-before)
          "There are asset db items")
      (fn []
        (is (= items-before (list-items)))))))

(defn no-empty-transactions
  "Add a transaction containing only transaction metadata, return a
  function which ensures that said transaction is not present in the
  restored db"
  []
  (let [uuid (java.util.UUID/randomUUID)]
    (tu/tx {:db/id "datomic.tx" :tx/author uuid})
    (fn []
      (is (empty? (d/q '[:find ?e
                         :in $ ?uuid
                         :where
                         [?e :tx/author ?uuid]]
                       (tu/db) uuid))))))

;; Add any new test setup/verify functions here
(def test-parts [#'file-user-seen
                 #'task-and-comment
                 #'compare-project-pull
                 #'asset-db-schema
                 #'no-empty-transactions])

(deftest restore-tx-backup
  (let [with-populated-db (tu/with-db)
        with-empty-db (tu/with-db {:migrate? false
                                   :mock-users? false
                                   :data-fixtures []})
        backup-file (java.io.File/createTempFile "testbackup" ".zip")
        verify-fns (atom nil)]
    (testing "Prepare populated database"
      (with-populated-db
        (fn []
          (reset! verify-fns
                  (doall
                   (for [setup test-parts]
                     (setup))))
          (with-open [out (io/output-stream backup-file)]
            (#'backup-ion/output-zip
             out
             ["transactions.edn" (partial #'backup-ion/output-all-tx (tu/connection))]
             (when (environment/feature-enabled? :asset-db)
               ["assets.edn" (partial #'backup-ion/output-all-tx
                                      (tu/asset-connection))]))))))

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
                                         :asset-conn (tu/asset-connection)
                                         :file backup-file})

          ;; Run all verify-fns to check state after restore
          (doseq [verify @verify-fns]
            (verify)))))))

(deftest delete-tx-backup
  (let [timestamp (System/currentTimeMillis)
        with-populated-db (tu/with-db {:timestamp timestamp :skip-delete? true})
        test-db-name (str "test-db-" timestamp)
        test-asset-db-name (str "test-asset-db-" timestamp)]
    (testing "Delete given databases by names"
      (with-populated-db
        (fn []
          (let [client (d/client (environment/config-value :datomic :client))]
            (#'backup-ion/delete-datomic-dbs
              {:conn (tu/connection)
               :asset-conn (tu/asset-connection)
               :datomic-client client
               :db-name test-db-name
               :asset-db-name test-asset-db-name})
            (is (nil?
                  (some #(or
                           (= test-asset-db-name %)
                           (= test-db-name %))
                    (d/list-databases client {})))
              "No test DB or Asset DB after ION delete db call")))))))


;; use from repl as in (restore-dev-backup-to-local "/tmp/teet-dev-backup-2020-12-15.edn.zip")
;; - after calling this, note the printed db name, and put that in your config.edn & restart
(defn restore-dev-backup-to-local
  [backup-file-path]
  (let [with-empty-db (tu/with-db {:migrate? false
                                   :mock-users? false
                                   :skip-delete? true
                                   :data-fixtures []})
        verify-fns (atom nil)]

    (with-empty-db
      (fn []
        (assert (empty? (d/q '[:find ?p :where [?p :db/ident :thk.project/id]]
                             (tu/db)))
                "Database is empty before restore")
        (#'backup-ion/restore-tx-file {:conn (tu/connection)
                                       :file (clojure.java.io/file backup-file-path)})))))
