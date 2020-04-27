(ns ^:db teet.thk.thk-import-test
  (:require [clojure.test :refer [deftest use-fixtures is testing]]
            [teet.thk.thk-import :as thk-import]
            [clojure.string :as str]
            [teet.test.utils :as tu]
            [datomic.client.api :as d]
            [teet.thk.thk-export :as thk-export]
            [teet.util.datomic :as du]))

(use-fixtures :once
  tu/with-global-data
  tu/with-environment
  (tu/with-db
    ;; Skip data fixtures, we want clean migrated db
    {:data-fixtures []}))

(def test-csv
  (str/join
   "\n"
   ["object_id;object_groupfk;object_groupshortname;object_groupname;object_roadnr;object_carriageway;object_kmstart;object_kmend;object_bridgenr;object_name;object_projectname;object_owner;object_regionfk;object_regionname;object_thkupdstamp;object_teetupdstamp;object_statusfk;object_statusname;phase_id;phase_teetid;phase_typefk;phase_shortname;phase_eststart;phase_estend;phase_thkupdstamp;phase_teetupdstamp;phase_cost;activity_id;activity_teetid;activity_taskid;activity_taskdescr;activity_typefk;activity_shortname;activity_statusfk;activity_statusname;activity_contract;activity_eststart;activity_estend;activity_actualstart;activity_actualend;activity_guaranteeexpired;activity_thkupdstamp;activity_teetupdstamp;activity_teetdelstamp;activity_cost;activity_procurementno;activity_procurementid"
    "11111;1107;SILD;silla remont;15128;1;19.057;19.117;176;test sild project 1;;1234567890;1801;Ida;2020-04-07 12:25:54.166;2020-04-07 13:35:08.0;1605;Kinnitatud;13906;26432259531738205;4099;projetapp;2020-04-07;2022-06-25;2020-04-07 13:43:28.959;2020-04-07 13:35:08.0;2.00;15906;52772160086740303;;;4004;Maaost;4100;Ettevalmistamisel;false;2021-04-12;2022-06-25;;;;2020-03-02 17:57:15.0;2020-04-07 13:35:08.0;;0.00;;"
    "11111;1107;SILD;silla remont;15128;1;19.057;19.117;176;test sild project 1;;1234567890;1801;Ida;2020-04-07 12:25:54.166;2020-04-07 13:35:08.0;1605;Kinnitatud;13906;26432259531738205;4099;projetapp;2020-04-07;2022-06-25;2020-04-07 13:43:28.959;2020-04-07 13:35:08.0;2.00;6594;71041645293866078;;;4003;Põhiprojekt;4102;Töös;true;2021-01-01;2020-06-12;2020-01-14;;;2020-03-11 21:40:26.603836;2020-04-07 13:35:08.0;;111000.00;666666;666"
    "11111;1107;SILD;silla remont;15128;1;19.057;19.117;176;test sild project 1;;1234567890;1801;Ida;2020-04-07 12:25:54.166;2020-04-07 13:35:08.0;1605;Kinnitatud;13905;49763896273144927;4098;ehitetapp;2021-01-01;2022-12-31;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;685000.00;5455;51413163714808928;;;4005;Teostus;4106;Hankeplaanis;false;2021-01-01;2022-12-31;;;;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;;640000.00;;777"
    "22222;1104;REK;rekonstrueerimine;6;1;69.937;72.162;;test rek project 2;;3344556677;1803;Lääne;2019-11-01 09:37:44.915;2020-03-12 11:39:01.0;1605;Kinnitatud;13958;62676560829746095;4099;projetapp;2020-07-06;2021-11-30;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;60000.00;6000;7133631441013680;;;4003;Põhiprojekt;4106;Hankeplaanis;false;2020-07-06;2021-08-24;;;;2020-03-11 21:40:26.603836;2020-04-07 13:35:08.0;;215000.00;;888"
    "22222;1104;REK;rekonstrueerimine;6;1;69.937;72.162;;test rek project 2;;3344556677;1803;Lääne;2019-11-01 09:37:44.915;2020-03-12 11:39:01.0;1605;Kinnitatud;13957;7621814603746225;4098;ehitetapp;2022-06-27;2023-11-30;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;2446500.00;5488;11390940463762354;;;4005;Teostus;4100;Ettevalmistamisel;false;2022-06-27;2023-11-30;;;;2020-03-11 21:40:26.603836;2020-03-12 11:39:01.0;;2350000.00;;"
    "33333;1108;LOK;liiklusohtliku koha kõrvaldamine;23;1;2.300;2.700;;test lok project 3;;9483726473;1801;Ida;2020-04-07 11:48:42.279;2020-04-07 13:35:08.0;1605;Kinnitatud;15921;;4099;projetapp;2020-04-13;2021-04-21;2020-04-07 08:19:28.791;;16000.00;;;;;;;;;;;;;;;;;;;;"
    "44444;1104;REK;rekonstrueerimine;20;1;2.000;4.000;;test rek project 4;;;1801;Ida;2020-04-07 11:24:10.297;2020-04-07 13:35:08.0;1605;Kinnitatud;15927;;4099;projetapp;2020-04-07;2020-07-07;2020-04-07 11:23:25.742;;44000.00;;;;;;;;;;;;;;;;;;;;"
    "55555;1104;REK;rekonstrueerimine;20;1;0.000;10.000;;test rek project 5;;66666666666;1803;Lääne;2020-04-07 12:06:57.739;2020-04-07 13:35:08.0;1605;Kinnitatud;15932;;4099;projetapp;2020-04-21;2020-05-10;2020-04-07 12:05:33.862;;80000.00;;;;;;;;;;;;;;;;;;;;"]))


(deftest thk<->teet-import-export
  (testing "Initially there are no projects"
    (is (empty? (d/q '[:find ?e :where [?e :thk.project/id _]]
                     (tu/db)))))
  (testing "THK -> TEET import"
    (let [projects (thk-import/parse-thk-export-csv
                    (java.io.ByteArrayInputStream.
                     (.getBytes test-csv)))]
      (is (= 5 (count projects)))
      (testing "Import projects"
        (thk-import/import-thk-projects! (tu/connection) "test://test-csv" projects))

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
          (testing "Project 1 is owned by existing Danny"
            (let [{owner :thk.project/owner :as _p1}
                  (d/pull db '[{:thk.project/owner [*]}]
                          [:thk.project/id "11111"])]
              (is (= {:user/person-id "1234567890"
                      :user/given-name "Danny D."
                      :user/family-name "Manager"}
                     (select-keys owner [:user/person-id
                                         :user/given-name
                                         :user/family-name])))))
          (testing "Project 5 has newly created owner"
            (let [{owner :thk.project/owner :as _p5}
                  (d/pull db '[* {:thk.project/owner [*]}]
                          [:thk.project/id "55555"])]
              (is (= {:user/person-id "66666666666"}
                     (select-keys owner [:user/person-id])))
              (is (= #{:db/id :user/person-id}
                     (set (keys owner))))))))))

  ;; Create tasks for p1 activity that is sent to THK
  (let [act-id (ffirst
                (d/q '[:find ?a
                       :where
                       [?p :thk.project/lifecycles ?l]
                       [?l :thk.lifecycle/activities ?a]
                       [?a :activity/name :activity.name/land-acquisition]
                       :in $ ?p]
                     (tu/db) [:thk.project/id "11111"]))]
    (is act-id "Project 1 has land acquisition activity")
    (tu/store-data! :act-id act-id)
    (tu/store-data! :task-id
                    (get-in (tu/tx {:db/id act-id
                                    :activity/tasks [{:db/id "new-task"
                                                      :task/type :task.type/third-party-review
                                                      :task/send-to-thk? true
                                                      :task/estimated-start-date #inst "2020-04-15T14:00:39.855-00:00"
                                                      :task/estimated-end-date  #inst "2020-06-25T14:00:39.855-00:00"}]})
                            [:tempids "new-task"])))

  (let [export (fn []
                 (let [[header & rows] (thk-export/export-thk-projects (tu/connection))]
                    (mapv #(zipmap header %) rows)))]
    (testing "Export CSV to THK has row for the task"
      ;; Export projects to THK, expect row for task
      (let [[task-row :as task-rows] (filter #(not (str/blank? (get % "activity_taskdescr")))
                                             (export))]
        (is (= 1 (count task-rows)) "there's exactly one task row")
        (is task-row "There is a row for the task")
        (is (= (get task-row "activity_taskdescr") "Ekspertiis")
            "task description is the estonian translation of task type")
        (is (str/blank? (get task-row "activity_id")) "task has no activity_id yet")
        (is (= (get task-row "activity_taskid") (str (tu/get-data :task-id))))
        (is (= (get task-row "activity_teetid") (str (tu/get-data :act-id))))))

    (testing "Changing task and exporting again keeps same ids"
      (tu/local-command tu/mock-user-boss
                        :task/update
                        {:db/id (tu/get-data :task-id)
                         :task/description "CHANGED DESCRIPTION"
                         :task/assignee {:user/id tu/internal-consultant-id}})
      (tu/entity (tu/get-data :task-id))
      (let [[task-row :as task-rows] (filter #(not (str/blank? (get % "activity_taskdescr")))
                                             (export))]
        (is (= 1 (count task-rows)) "still exactly one task row")
        (is (= (get task-row "activity_taskid") (str (tu/get-data :task-id))))
        (is (= (get task-row "activity_teetid") (str (tu/get-data :act-id))))))

    (testing "Changing activity and exporting again keeps the same ids"
      (tu/local-command
       tu/mock-user-boss
       :activity/update
       {:activity {:db/id (tu/get-data :act-id)
                   :activity/estimated-start-date #inst "2021-06-11T21:00:00.000-00:00"
                   :activity/estimated-end-date #inst "2022-04-24T21:00:00.000-00:00"}})
      (let [[task-row :as task-rows] (filter #(not (str/blank? (get % "activity_taskdescr")))
                                             (export))]
        (is (= 1 (count task-rows)) "still exactly one task row")
        (is (= (get task-row "activity_taskid") (str (tu/get-data :task-id))))
        (is (= (get task-row "activity_teetid") (str (tu/get-data :act-id))))))))
