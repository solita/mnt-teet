(ns ^:db teet.thk.thk-import-test
  (:require [clojure.test :refer [deftest use-fixtures is testing]]
            [teet.thk.thk-import :as thk-import]
            [clojure.string :as str]
            [teet.test.utils :as tu]
            [datomic.client.api :as d]
            [teet.thk.thk-export :as thk-export]
            [teet.util.collection :as cu]
            [teet.thk.thk-integration-ion :as thk-integration-ion]
            [teet.thk.thk-mapping :as thk-mapping]
            [clojure.java.io :as io]
            [teet.meta.meta-model :as meta-model]
            [teet.integration.integration-id :as integration-id]
            [teet.util.date :as date]))


(use-fixtures :each
  tu/with-global-data
  tu/with-environment
  (tu/with-db
    ;; Skip data fixtures, we want clean migrated db
    {:data-fixtures []}))

(defn import-projects-csv!
  ([] (import-projects-csv! (.getBytes (slurp "test/teet/thk/thk-test-data.csv"))))
  ([csv-data]
   (let [conn (tu/connection)
         projects (thk-import/parse-thk-export-csv
                    {:input (java.io.ByteArrayInputStream.
                             csv-data)
                     :column-mapping thk-mapping/thk->teet-project
                     :group-by-fn #(get % :thk.project/id)})
         import-result (thk-import/import-thk-projects! conn "test://test-csv" projects)]
     (tu/store-data! :projects-csv projects)
     (d/sync conn (get-in import-result [:db-after :t]))
     import-result)))

(defn import-contracts-csv!
  ([] (import-contracts-csv! (.getBytes (slurp "test/teet/thk/thk-test-data.csv"))))
  ([csv-data]
   (let [conn (tu/connection)
         contracts
         (thk-import/parse-thk-export-csv
           {:input (java.io.ByteArrayInputStream.
                     csv-data)
            :column-mapping thk-mapping/thk->teet-contract
            :group-by-fn (fn [val]
                           (select-keys val [:thk.contract/procurement-part-id
                                             :thk.contract/procurement-id]))})
         import-contract-result (thk-import/import-thk-contracts! conn "test://test-csv" contracts)]
     (tu/store-data! :contract-rows contracts)
     (d/sync conn (get-in import-contract-result [:db-after :t]))
     import-contract-result)))

(defn import-tasks-csv!
  ([] (import-tasks-csv! (.getBytes (slurp "test/teet/thk/thk-test-data.csv"))))
  ([csv-data]
   (let [conn (tu/connection)
         projects
         (thk-import/parse-thk-export-csv
           {:input (java.io.ByteArrayInputStream.
                     csv-data)
            :column-mapping thk-mapping/thk->teet-project
            :group-by-fn #(get % :thk.project/id)})
         import-tasks-result (thk-import/import-thk-tasks! conn "test://test-csv" projects)]
     (tu/store-data! :projects-csv projects)
     (d/sync conn (get-in import-tasks-result [:db-after :t]))
     import-tasks-result)))

(defn ->csv-data [csv]
  (:file (thk-integration-ion/csv->file {:csv csv})))

(defn export-csv []
  (let [[header & rows :as csv-data]
        (thk-export/export-thk-projects (tu/connection))
        rows (mapv #(zipmap header %) rows)]
    (tu/store-data! :export-rows rows)
    (tu/store-data! :export-csv csv-data)
    rows))

(defn- lc->act->task []
  ;; pull lifecycle/activity/task hierarchy with ids
  (into #{}
        (map (fn [lc]
               (-> lc first
                   (update :thk.lifecycle/activities
                           (fn [acts]
                             (into #{}
                                   (map (fn [act]
                                          {:activity-id (:db/id act)
                                           :task-ids (into #{} (map :db/id) (:activity/tasks act))}))
                                   acts))))))
        (d/q '[:find (pull ?lc [:db/id
                                {:thk.lifecycle/activities
                                 [:db/id
                                  {:activity/tasks [:db/id]}]}])
               :where [?lc :thk.lifecycle/id _]] (tu/db))))

(deftest thk<->teet-import-export
  (testing "Initially there are no projects"
    (is (empty? (d/q '[:find ?e :where [?e :thk.project/id _]]
                     (tu/db)))))
  (testing "THK -> TEET import"
    (import-projects-csv!)
    (import-contracts-csv!)
    (is (= 8 (count (tu/get-data :projects-csv))))

    (testing "Imported projects information is correct"
      (let [db (tu/db)]
        (testing "All non-excluded projects are found by id after import"
          ;; Also note that 66666, a TUGI project, is not present
          (is (=
               #{"11111" "22222" "33333" "44444" "55555" "77777" "790"}
               (into #{}
                     (map first)
                     (d/q '[:find ?id
                            :where [_ :thk.project/id ?id]]
                          db)))))

        (testing "All lifecycles and activities have integration"
          (is (every? #(contains? % :integration/id)
                      (map first
                           (d/q '[:find (pull ?e [:integration/id])
                                  :where (or [?e :thk.lifecycle/id _]
                                             [?e :thk.activity/id _])]
                                db)))))

        (testing "Project 1 is owned by existing Danny"
          (let [{owner :thk.project/owner :as _p1}
                (d/pull db '[{:thk.project/owner [*]}]
                        [:thk.project/id "11111"])]
            (is (= {:user/person-id "EE12345678900"
                    :user/given-name "Danny D."
                    :user/family-name "Manager"}
                   (select-keys owner [:user/person-id
                                       :user/given-name
                                       :user/family-name])))))
        (testing "Project 5 has newly created owner"
          (let [{owner :thk.project/owner :as _p5}
                (d/pull db '[* {:thk.project/owner [*]}]
                        [:thk.project/id "55555"])]
            (is (= {:user/person-id "EE66666666666"}
                   (select-keys owner [:user/person-id])))
            (is (= #{:db/id :user/person-id}
                   (set (keys owner))))))))

    (testing "Imported contracts information is correct"
      (let [db (tu/db)]
        (testing "Amount of contracts found is correct"
          (is (= 3 (count (d/q '[:find (pull ?c [*])
                                 :where [?c :thk.contract/procurement-id _]]
                               db)))))
        (testing "One of the contracts has a part name and the contract target exists"
          (is (= 1 (count (d/q '[:find ?c
                                 :where
                                 [?c :thk.contract/procurement-id _]
                                 [?c :thk.contract/procurement-part-id _]
                                 [?c :thk.contract/targets ?t]
                                 [?t :thk.activity/id "5455"]]
                               db))))))))

  ;; Create tasks for p1 activity that is sent to THK
  (let [act-id (ffirst
                (d/q '[:find ?a
                       :where
                       [?p :thk.project/lifecycles ?l]
                       [?l :thk.lifecycle/activities ?a]
                       [?a :activity/name :activity.name/detailed-design]
                       :in $ ?p]
                     (tu/db) [:thk.project/id "11111"]))]
    (is act-id "Project 1 has a detailed design activity")
    (tu/store-data! :act-id act-id)
    (tu/store-data! :act-uuid (java.util.UUID/randomUUID))
    (tu/store-data! :task-uuid (java.util.UUID/randomUUID))

    (tu/store-data!
     :task-id
     (get-in (tu/tx {:db/id act-id
                     :integration/id (tu/get-data :act-uuid)
                     :activity/tasks [{:db/id "new-task"
                                       :task/type :task.type/equipment
                                       :task/send-to-thk? true
                                       :integration/id (tu/get-data :task-uuid)
                                       :task/estimated-start-date #inst "2020-04-15T14:00:39.855-00:00"
                                       :task/estimated-end-date  #inst "2020-06-25T14:00:39.855-00:00"}]})
             [:tempids "new-task"])))

  (testing "Export CSV to THK has row for the task"
    ;; Export projects to THK, expect row for task
    (let [[task-row :as task-rows] (filter #(not (str/blank? (get % "activity_taskdescr")))
                                           (export-csv))]
      (is (= 1 (count task-rows)) "there's exactly one task row")
      (is task-row "There is a row for the task")
      (is (= (get task-row "activity_taskdescr") "Seadmed")
          "task description is the estonian translation of task type")
      (is (str/blank? (get task-row "activity_id")) "task has no activity_id yet")
      (is (= (get task-row "activity_taskid")
             (str (integration-id/uuid->number (tu/get-data :task-uuid)))))
      (is (= (get task-row "activity_teetid")
             (str (integration-id/uuid->number (tu/get-data :act-uuid)))))))

  (testing "Changing task and exporting again keeps same ids"
    (tu/local-command tu/mock-user-boss
                      :task/update
                      {:db/id (tu/get-data :task-id)
                       :task/description "CHANGED DESCRIPTION"
                       :task/assignee {:user/id tu/internal-consultant-id}})
    (tu/entity (tu/get-data :task-id))
    (let [[task-row :as task-rows] (filter #(not (str/blank? (get % "activity_taskdescr")))
                                           (export-csv))]
      (is (= 1 (count task-rows)) "still exactly one task row")
      (is (= (get task-row "activity_taskid")
             (str (integration-id/uuid->number (tu/get-data :task-uuid)))))
      (is (= (get task-row "activity_teetid")
             (str (integration-id/uuid->number (tu/get-data :act-uuid)))))))

  (testing "Changing activity and exporting again keeps the same ids"
    (tu/local-command
     tu/mock-user-boss
     :activity/update
     {:activity {:db/id (tu/get-data :act-id)
                 :activity/estimated-start-date #inst "2021-06-11T21:00:00.000-00:00"
                 :activity/estimated-end-date #inst "2022-04-24T21:00:00.000-00:00"}})
    (let [[task-row :as task-rows] (filter #(not (str/blank? (get % "activity_taskdescr")))
                                           (export-csv))]
      (is (= 1 (count task-rows)) "still exactly one task row")
      (is (= (get task-row "activity_taskid")
             (str (integration-id/uuid->number (tu/get-data :task-uuid)))))
      (is (= (get task-row "activity_teetid")
             (str (integration-id/uuid->number (tu/get-data :act-uuid))))))))

(defn set-csv-column [csv row-test-column row-test-value set-col set-val]
  (let [test-col-idx (cu/find-idx #(= row-test-column %) thk-mapping/csv-column-names)
        set-col-idx (cu/find-idx #(= set-col %) thk-mapping/csv-column-names)]

    (mapv (fn [row]
            (if-not (= (nth row test-col-idx) row-test-value)
              row
              (do (println "SETTING FOR ROW: " row)
                  (println "VALUE CHANGE" (nth row set-col-idx) " => " set-val)
                  (assoc row set-col-idx set-val))))
          csv)))

(deftest activity-id-round-trip
  (import-projects-csv!)

  ;; Create new activity
  (let [{:keys [tempids]}
        (tu/local-command tu/mock-user-boss
                      :activity/create
                      {:lifecycle-id (:db/id (tu/entity [:thk.lifecycle/id "15921"]))
                       :activity {:activity/name :activity.name/detailed-design
                                  :activity/estimated-start-date #inst "2020-04-13T21:00:00.000-00:00"
                                  :activity/estimated-end-date #inst "2021-04-19T20:00:00.000-00:00"}
                       :tasks [[:task.group/design-reports :task.type/calculation false]]})

        act-id (tempids "new-activity")
        act-uuid (ffirst (d/q '[:find ?uuid
                                :where [?e :integration/id ?uuid]
                                :in $ ?e]
                              (tu/db)
                              act-id))]
    (is (number? act-id))
    (is (uuid? act-uuid) "Created activity has an integration id")
    (tu/store-data! :act-id act-id)
    (tu/store-data! :act-uuid act-uuid))

  (is (some? (tu/get-data :act-id)) "new activity was created")

  (testing "Exporting has activity without THK id"
    (export-csv)
    (let [rows (tu/get-data :export-rows)
          activity-ids (into #{}
                             (map #(get % "activity_id")
                                  rows))]
      (is (= #{"" "6000" "5488" "6594" "5455" "896" "897"} activity-ids)
          "rows have all allowed THK activity ids and empty")
      (is (not (activity-ids "15906"))
          "THK activity 15906 is not present, as it's type is land acquisition, one of the types not sent to THK")))

  (testing "Importing again with THK id sets id"
    (let [csv (tu/get-data :export-csv)
          act-teet-id (tu/get-data :act-uuid)
          csv-with-id (set-csv-column csv
                                      "activity_teetid" (str (integration-id/uuid->number act-teet-id))
                                      "activity_id" "99999")
          csv-data (->csv-data csv-with-id)
          lc->act->task-before (lc->act->task)]
      (println "BEF:" lc->act->task-before)
      (import-projects-csv! csv-data)
      (is (= lc->act->task-before (lc->act->task))
          "lifecycle/activity/task counts hasn't changed")
      (is (= "99999" (-> :act-id tu/get-data tu/entity :thk.activity/id))
          "existing activity has the THK id")))

  (testing "Roundtrip task keeps same lifecycle/activity/task hierarchy"
    ;; TEET-517 test level violation, task shouldn't be directly linked to
    ;; lifecycle, it should only be under activity
    (tu/store-data! :task-uuid (java.util.UUID/randomUUID))
    (tu/store-data!
     :task-id
     (get-in
      (tu/tx {:db/id (-> :act-id tu/get-data tu/entity :db/id)
              :activity/tasks [{:db/id "new-task"
                                :integration/id (tu/get-data :task-uuid)
                                :task/type :task.type/third-party-review
                                :task/send-to-thk? true
                                :task/estimated-start-date #inst "2020-04-15T14:00:39.855-00:00"
                                :task/estimated-end-date  #inst "2020-06-25T14:00:39.855-00:00"}]})
      [:tempids "new-task"]))
    (export-csv)
    (let [csv-with-task-id
          (set-csv-column
           (tu/get-data :export-csv)
           ;; when taskid is our created task
           "activity_taskid" (str (integration-id/uuid->number (tu/get-data :task-uuid)))
           ;; mock THK generated activity id
           "activity_id" "99887")
          hierarchy-before (lc->act->task)]
      (io/copy (java.io.ByteArrayInputStream. (->csv-data csv-with-task-id))
               (io/file "testi.csv"))
      (import-projects-csv! (->csv-data csv-with-task-id))
      (is (= hierarchy-before (lc->act->task)) "hierarchy hasn't changed")
      (let [lifecycle-tasks (mapv first
                                  (d/q '[:find (pull ?task [*])
                                         :where
                                         [_ :thk.lifecycle/activities ?task]
                                         [?task :task/type _]] (tu/db)))]
        (is (empty? lifecycle-tasks)))
      (is (= "99887" (-> :task-id tu/get-data tu/entity :thk.activity/id))
          "Task activity id has been set")
      (io/delete-file "testi.csv"))))

(deftest activity-procurement-data ;; TEET-605
  (import-projects-csv!)

  (testing "procurement nr and procurement id are not imported into activity"
    (let [activity (ffirst (d/q '[:find (pull ?a [*])
                                  :where [?a :thk.activity/id "6594"]]
                                (tu/db)))]
      (def activity* activity)
      (is (= (:activity/procurement-id activity) nil))
      (is (= (:activity/procurement-nr activity) nil))))

  (testing "procurement nr and id are not exported from activity"
    (export-csv)
    (let [export-rows (tu/get-data :export-rows)
          activity-row (cu/find-first #(= (get % "activity_id")
                                          "6594")
                                      export-rows)]
      (is (= (get activity-row "activity_procurementid") ""))
      (is (= (get activity-row "activity_procurementno") "")))))

(deftest activity-deletion-timestamp
  (import-projects-csv!)

  (tu/tx (meta-model/deletion-tx tu/mock-user-boss
                                 (:db/id (tu/entity [:thk.activity/id "6594"]))))

  (println "testing export:")
  (testing "Exported row has deletion timestamp"
    (export-csv)
    (println "export csv success")
    (let [{:strs [activity_teetdelstamp] :as row}
          (cu/find-first #(= (get % "activity_id") "6594")
                         (tu/get-data :export-rows))]
      (println "activity teet del stamp: " row)
      (is (some? activity_teetdelstamp) "Activity deletion stamp is present"))))

(deftest add-tasks-from-thk-activities
  (import-projects-csv!)
  ; Store the Construction activity-id of project 790
  (let [activity-id (ffirst
                 (d/q '[:find ?a
                        :where
                        [?p :thk.project/lifecycles ?l]
                        [?l :thk.lifecycle/activities ?a]
                        [?a :activity/name :activity.name/construction]
                        :in $ ?p]
                   (tu/db) [:thk.project/id "790"]))]
    (println "Construction activity-id for 790 project/id is " activity-id)
    (tu/store-data! :construction-activity-id activity-id)

    ; Set integration/id for the Construction Activity so, it can be found during importing tasks
    (tu/tx {:db/id (-> :construction-activity-id tu/get-data tu/entity :db/id)
            :integration/id #uuid "00000000-0000-0000-2D60-CC14A81144C6"})

    ; Check no tasks belongs to Construction Activity of 790 project before tasks import
    (let [no-tasks (d/q '[:find ?tasks
                          :where [?e :activity/tasks ?tasks]
                          :in $ ?e] (tu/db) activity-id)]
      (testing (is (= 0 (count no-tasks))) "No new tasks created after projects import" ))

    ; Import new tasks
    (import-tasks-csv!)

    ; Verify new task count
    (let [new-construction-activity-tasks
          (d/q '[:find ?tasks
                 :where [?e :activity/tasks ?tasks]
                 :in $ ?e] (tu/db) activity-id)
          task-with-actual-start-date (ffirst (d/q '[:find (pull ?tasks [:task/actual-start-date])
                                                     :where [?e :activity/tasks ?tasks]
                                                     [?tasks :task/actual-start-date _]
                                                     :in $ ?e] (tu/db) activity-id))
          expected-actual-start-date (date/->start-of-date 2022 9 20)]
      (println "New Construction Activity tasks: " (ffirst new-construction-activity-tasks))
      (testing (is (= 3 (count new-construction-activity-tasks))) "After tasks import 3 new tasks imported")
      (testing (is (= expected-actual-start-date (:task/actual-start-date task-with-actual-start-date)))
        "Actual start date has been imported"))))
