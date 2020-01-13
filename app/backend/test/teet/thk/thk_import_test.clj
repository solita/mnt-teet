(ns teet.thk.thk-import-test
  (:require [clojure.test :refer :all]
            [teet.thk.thk-import :as thk-import]
            [clojure.string :as str]
            [clojure.java.io :as io]))


(def test-csv
  (str/join
   "\n"
   ["ob_id;plangroupfk;shortname;gr_nimi;roadnr;carriageway;kmstart;kmend;bridgenr;objectname;projname;owner;regionfk;reg_nimi;ob_thkupd;ob_teetupd;objectstatusfk;ob_status;ph_id;ph_teetid;ph_typefk;ph_shname;ph_estStart;ph_estEnd;ph_thkUpd;ph_teetupd;ph_cost;act_id;act_teetid;act_typefk;at_shname;act_statusfk;act_statname;act_contract;act_estStart;act_estEnd;act_actualStart;act_actualEnd;act_guarExpired;act_thkUpd;act_teetUpd;act_teetDel;actfin_id;act_fs;act_invcd;ac_cost;proc_No;proc_id"
    "666;1108;LOK;liiklusohtliku koha kõrvaldamine;2;1;191.5;191.7;;Test Project;;rolf.teflon;1802;Lõuna;2019-11-27 16:28:47;;1605;Kinnitatud;8708;;4099;projetapp;2017-01-01;2019-10-01;2019-12-27 15:00:00;;76904;128;;4003;Põhiprojekt;4104;Lõpetatud;true;2017-01-01;2017-05-10;2017-02-03;2017-08-28;;2018-05-24 16:25:11;;;;;;11760;;"
    "666;1108;LOK;liiklusohtliku koha kõrvaldamine;2;1;191.5;191.7;;Test Project;;rolf.teflon;1802;Lõuna;2019-11-27 16:28:47;;1605;Kinnitatud;8708;;4099;projetapp;2017-01-01;2019-10-01;2019-12-27 15:00:00;;76904;6436;;4003;Põhiprojekt;4102;Töös;true;2018-08-07;2019-09-30;2018-07-23;;;2018-08-14 10:46:33;;;;;;61644;198865;479"
    "666;1108;LOK;liiklusohtliku koha kõrvaldamine;2;1;191.5;191.7;;Test Project;;rolf.teflon;1802;Lõuna;2019-11-27 16:28:47;;1605;Kinnitatud;8708;;4099;projetapp;2017-01-01;2019-10-01;2019-12-27 15:00:00;;76904;6464;;4011;LOA proj;4100;Ettevalmistamisel;false;2019-08-01;2019-10-01;;;;2018-08-24 09:00:28;;;;;;3500;;"
    "666;1108;LOK;liiklusohtliku koha kõrvaldamine;2;1;191.5;191.7;;Test Project;;rolf.teflon;1802;Lõuna;2019-11-27 16:28:47;;1605;Kinnitatud;14092;;4098;ehitetapp;2022-03-01;2022-12-31;2019-12-27 15:00:00;;120100;5877;;4005;Teostus;4100;Ettevalmistamisel;false;2022-03-01;2022-12-31;;;;2019-11-11 15:50:10;;;;;;120100;;"]))

(def project-keys #{:thk.project/estimated-start-date :thk.project/estimated-end-date
                    :thk.project/road-nr :thk.project/start-m :thk.project/end-m
                    :thk.project/carriageway
                    :thk.project/name :thk.project/id})

(def phase-keys #{:thk.lifecycle/id :thk.lifecycle/type
                  :thk.lifecycle/estimated-start-date
                  :thk.lifecycle/estimated-end-date})

(deftest csv-to-project-datomic-attributes
  (let [csv (thk-import/parse-thk-export-csv (java.io.ByteArrayInputStream. (.getBytes test-csv)))]
    (is (= '("666") (keys csv)) "Parsed CSV contains one project")
    (let [{phases :thk.project/lifecycles :as project}
          (thk-import/project-datomic-attributes (first csv))

          [design-phase construction-phase]
          (sort-by :thk.lifecycle/estimated-start-date phases)]

      (is (= (select-keys project project-keys)
             {:thk.project/estimated-start-date #inst "2016-12-31T22:00:00.000-00:00"
              :thk.project/estimated-end-date #inst "2022-12-30T22:00:00.000-00:00"
              :thk.project/start-m 191500,
              :thk.project/name "Test Project",
              :thk.project/carriageway 1,
              :thk.project/id "666",
              :thk.project/road-nr 2,
              :thk.project/end-m 191700}))

      (is (= (select-keys design-phase phase-keys)
             #:thk.lifecycle {:id "8708",
                              :type :thk.lifecycle-type/design,
                              :estimated-start-date #inst "2016-12-31T22:00:00.000-00:00"
                              :estimated-end-date #inst "2019-09-30T21:00:00.000-00:00"}))

      (is (= 2 (count (:thk.lifecycle/activities design-phase))))

      (is (= (select-keys construction-phase phase-keys)
             #:thk.lifecycle {:id "14092"
                              :type :thk.lifecycle-type/construction
                              :estimated-start-date #inst "2022-02-28T22:00:00.000-00:00"
                              :estimated-end-date #inst "2022-12-30T22:00:00.000-00:00"}))

      (is (zero? (count (:thk.lifecycle/activities construction-phase)))))))
