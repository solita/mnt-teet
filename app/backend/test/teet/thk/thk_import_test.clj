(ns ^:db teet.thk.thk-import-test
  (:require [clojure.test :refer [deftest use-fixtures is testing]]
            [teet.thk.thk-import :as thk-import]
            [clojure.string :as str]
            [teet.test.utils :as tu]
            [datomic.client.api :as d]
            [teet.thk.thk-export :as thk-export]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.thk.thk-integration-ion :as thk-integration-ion]
            [teet.util.collection :as cu]
            [teet.thk.thk-mapping :as thk-mapping]
            [clojure.java.io :as io]
            [teet.meta.meta-query :as meta-query]
            [teet.meta.meta-model :as meta-model]))

(use-fixtures :each
  tu/with-global-data
  tu/with-environment
  (tu/with-db
    ;; Skip data fixtures, we want clean migrated db
    {:data-fixtures []}))

(def test-csv
  (str/join
   "\n"
   ["object_id;object_groupfk;object_groupshortname;object_groupname;object_roadnr;object_carriageway;object_kmstart;object_kmend;object_bridgenr;object_name;object_projectname;object_owner;object_regionfk;object_regionname;object_thkupdstamp;object_teetupdstamp;object_statusfk;object_statusname;phase_id;phase_teetid;phase_typefk;phase_shortname;phase_eststart;phase_estend;phase_thkupdstamp;phase_teetupdstamp;phase_cost;activity_id;activity_teetid;activity_taskid;activity_taskdescr;activity_typefk;activity_shortname;activity_statusfk;activity_statusname;activity_contract;activity_eststart;activity_estend;activity_actualstart;activity_actualend;activity_guaranteeexpired;activity_thkupdstamp;activity_teetupdstamp;activity_teetdelstamp;activity_cost;activity_procurementno;activity_procurementid"
    "11111;1107;SILD;silla remont;15128;1;19.057;19.117;176;test sild project 1;;12345678900;1801;Ida;2020-04-07 12:25:54.166;2020-04-07 13:35:08.0;1605;Kinnitatud;13906;;4099;projetapp;2020-04-07;2022-06-25;2020-04-07 13:43:28.959;2020-04-07 13:35:08.0;2.00;15906;;;;4004;Maaost;4100;Ettevalmistamisel;false;2021-04-12;2022-06-25;;;;2020-03-02 17:57:15.0;2020-04-07 13:35:08.0;;0.00;;"
    "11111;1107;SILD;silla remont;15128;1;19.057;19.117;176;test sild project 1;;12345678900;1801;Ida;2020-04-07 12:25:54.166;2020-04-07 13:35:08.0;1605;Kinnitatud;13906;;4099;projetapp;2020-04-07;2022-06-25;2020-04-07 13:43:28.959;2020-04-07 13:35:08.0;2.00;6594;;;;4003;Põhiprojekt;4102;Töös;true;2021-01-01;2020-06-12;2020-01-14;;;2020-03-11 21:40:26.603836;2020-04-07 13:35:08.0;;111000.00;666666;666"
    "11111;1107;SILD;silla remont;15128;1;19.057;19.117;176;test sild project 1;;12345678900;1801;Ida;2020-04-07 12:25:54.166;2020-04-07 13:35:08.0;1605;Kinnitatud;13905;;4098;ehitetapp;2021-01-01;2022-12-31;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;685000.00;5455;;;;4005;Teostus;4106;Hankeplaanis;false;2021-01-01;2022-12-31;;;;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;;640000.00;;777"
    "22222;1104;REK;rekonstrueerimine;6;1;69.937;72.162;;test rek project 2;;3344556677;1803;Lääne;2019-11-01 09:37:44.915;2020-03-12 11:39:01.0;1605;Kinnitatud;13958;;4099;projetapp;2020-07-06;2021-11-30;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;60000.00;6000;;;;4003;Põhiprojekt;4106;Hankeplaanis;false;2020-07-06;2021-08-24;;;;2020-03-11 21:40:26.603836;2020-04-07 13:35:08.0;;215000.00;;888"
    "22222;1104;REK;rekonstrueerimine;6;1;69.937;72.162;;test rek project 2;;3344556677;1803;Lääne;2019-11-01 09:37:44.915;2020-03-12 11:39:01.0;1605;Kinnitatud;13957;;4098;ehitetapp;2022-06-27;2023-11-30;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;2446500.00;5488;;;;4005;Teostus;4100;Ettevalmistamisel;false;2022-06-27;2023-11-30;;;;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;;2350000.00;;"
    "33333;1108;LOK;liiklusohtliku koha kõrvaldamine;23;1;2.300;2.700;;test lok project 3;;9483726473;1801;Ida;2020-04-07 11:48:42.279;2020-04-07 13:35:08.0;1605;Kinnitatud;15921;;4099;projetapp;2020-04-13;2021-04-21;2020-04-07 08:19:28.791;;16000.00;;;;;;;;;;;;;;;;;;;;"
    "44444;1104;REK;rekonstrueerimine;20;1;2.000;4.000;;test rek project 4;;;1801;Ida;2020-04-07 11:24:10.297;2020-04-07 13:35:08.0;1605;Kinnitatud;15927;;4099;projetapp;2020-04-07;2020-07-07;2020-04-07 11:23:25.742;;44000.00;;;;;;;;;;;;;;;;;;;;"
    "55555;1104;REK;rekonstrueerimine;20;1;0.000;10.000;;test rek project 5;;66666666666;1803;Lääne;2020-04-07 12:06:57.739;2020-04-07 13:35:08.0;1605;Kinnitatud;15932;;4099;projetapp;2020-04-21;2020-05-10;2020-04-07 12:05:33.862;;80000.00;;;;;;;;;;;;;;;;;;;;"]))

(defn import-csv!
  ([] (import-csv! (.getBytes test-csv)))
  ([csv-data]
   (let [conn (tu/connection)
         projects (thk-import/parse-thk-export-csv
                   (java.io.ByteArrayInputStream.
                    csv-data))
         import-result (thk-import/import-thk-projects! conn "test://test-csv" projects)]
     (tu/store-data! :projects-csv projects)
     (d/sync conn (get-in import-result [:db-after :t]))
     import-result)))

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
    (import-csv!)
    (is (= 5 (count (tu/get-data :projects-csv))))

    (testing "Imported projects information is correct"
      (let [db (tu/db)]
        (testing "All projects are found by id after import"
          (is (=
               #{"11111" "22222" "33333" "44444" "55555"}
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
                   (set (keys owner)))))))))

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
                                       :task/type :task.type/design-requirements
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
      (is (= (get task-row "activity_taskdescr") "Projekteerimistingimused")
          "task description is the estonian translation of task type")
      (is (str/blank? (get task-row "activity_id")) "task has no activity_id yet")
      (is (= (get task-row "activity_taskid")
             (str (thk-mapping/uuid->number (tu/get-data :task-uuid)))))
      (is (= (get task-row "activity_teetid")
             (str (thk-mapping/uuid->number (tu/get-data :act-uuid)))))))

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
             (str (thk-mapping/uuid->number (tu/get-data :task-uuid)))))
      (is (= (get task-row "activity_teetid")
             (str (thk-mapping/uuid->number (tu/get-data :act-uuid)))))))

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
             (str (thk-mapping/uuid->number (tu/get-data :task-uuid)))))
      (is (= (get task-row "activity_teetid")
             (str (thk-mapping/uuid->number (tu/get-data :act-uuid))))))))

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
  (import-csv!)

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
      (is (= #{"" "6000" "5488" "6594" "5455"} activity-ids)
          "rows have all allowed THK activity ids and empty")
      (is (not (activity-ids "15906"))
          "THK activity 15906 is not present, as it's type is land acquisition, one of the types not sent to THK")))

  (testing "Importing again with THK id sets id"
    (let [csv (tu/get-data :export-csv)
          act-teet-id (tu/get-data :act-uuid)
          csv-with-id (set-csv-column csv
                                      "activity_teetid" (str (thk-mapping/uuid->number act-teet-id))
                                      "activity_id" "99999")
          csv-data (->csv-data csv-with-id)
          lc->act->task-before (lc->act->task)]
      (println "BEF:" lc->act->task-before)
      (import-csv! csv-data)
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
           "activity_taskid" (str (thk-mapping/uuid->number (tu/get-data :task-uuid)))
           ;; mock THK generated activity id
           "activity_id" "99887")
          hierarchy-before (lc->act->task)]
      (io/copy (java.io.ByteArrayInputStream. (->csv-data csv-with-task-id))
               (io/file "testi.csv"))
      (import-csv! (->csv-data csv-with-task-id))
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
  (import-csv!)

  (testing "procurement nr and procurement id are imported"
    (let [activity (ffirst (d/q '[:find (pull ?a [*])
                                  :where [?a :thk.activity/id "6594"]]
                                (tu/db)))]
      (is (= (:activity/procurement-id activity) "666"))
      (is (= (:activity/procurement-nr activity) "666666"))))

  (testing "procurement nr and id are exported"
    (export-csv)
    (let [export-rows (tu/get-data :export-rows)
          activity-row (cu/find-first #(= (get % "activity_id")
                                          "6594")
                                      export-rows)]
      (is (= (get activity-row "activity_procurementid") "666"))
      (is (= (get activity-row "activity_procurementno") "666666")))))

(deftest activity-deletion-timestamp
  (import-csv!)

  (tu/tx (meta-model/deletion-tx tu/mock-user-boss (:db/id (tu/entity [:thk.activity/id "6594"]))))

  (testing "Exported row has deletion timestamp"
    (export-csv)
    (def *export (tu/get-data :export-rows))
    (let [{:strs [activity_teetdelstamp] :as row} (cu/find-first #(= (get % "activity_id") "6594")
                                                                 (tu/get-data :export-rows))]
      (println "activity teet del stamp: " row)
      (is (some? activity_teetdelstamp) "Activity deletion stamp is present"))))
