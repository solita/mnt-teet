(ns teet.contract.contract-queries
  (:require [teet.db-api.core :refer [defquery]]
            [datomic.client.api :as d]
            [teet.util.collection :as cu]
            [clojure.string :as str]))

(def dummy-data                                             ;; TODO just used for development
  [{:db/id 92358976735452,
    :thk.contract/procurement-id "1565",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-3922-9f38fa745f67",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "231269",
                            :activity/estimated-start-date #inst"2021-03-01T00:00:00.000-00:00",
                            :thk.activity/id "989",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"3077838.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1565",
                            :activity/estimated-end-date #inst"2021-11-01T00:00:00.000-00:00",
                            :db/id 74766790689908,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 69 Võru- Kuigatsi- Tõrva, km 50.0-57.0, rekonstrueerimine"}
   {:db/id 92358976735453,
    :thk.contract/procurement-id "1933",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690099,
                            :thk.activity/id "19139",
                            :task/estimated-start-date #inst"2020-12-14T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-01-29T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-12-29T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-57bf-9882201c31ea"}
                           {:db/id 74766790690100,
                            :thk.activity/id "19138",
                            :task/estimated-start-date #inst"2020-12-14T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-01-29T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-12-29T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-4016-9ea55804d14d"}],
    :thk.contract/name "Maantee nr 59 Pärnu - Tori km 20,800 Tori silla ehituse põhiprojekti ekspertiisi ja liiklusohutusauditi koostamine"}
   {:db/id 92358976735454,
    :thk.contract/procurement-id "1859",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-6449-75248bbe4d3f",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "e344444",
                            :activity/estimated-start-date #inst"2021-01-04T00:00:00.000-00:00",
                            :thk.activity/id "18804",
                            :activity/integration-info "#:activity{:contract \"false\", :cost \"420000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1859",
                            :activity/estimated-end-date #inst"2021-08-31T00:00:00.000-00:00",
                            :db/id 74766790690451,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 21108 Lõmala - Kaugatoma km 0.0-10.8 remont lisasin reavahetuse",
    :thk.contract/part-name "leping111",
    :thk.contract/procurement-part-id "4"}
   {:db/id 92358976735455,
    :thk.contract/procurement-id "882",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-79d8-ee479f361ec7",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "231551",
                            :activity/estimated-start-date #inst"2021-03-03T00:00:00.000-00:00",
                            :thk.activity/id "1446",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"2687993.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "882",
                            :activity/estimated-end-date #inst"2021-12-31T00:00:00.000-00:00",
                            :db/id 74766790689450,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee nr 11310 Aruvalla- Jägala km 11,6-17,8 rekonstrueerimine"}
   {:db/id 92358976735456,
    :thk.contract/procurement-id "1929",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690820,
                            :thk.activity/id "19146",
                            :task/estimated-start-date #inst"2021-03-01T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-06-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-05-03T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-1a43-de4139769e65"}],
    :thk.contract/name "Riigitee nr 5 Pärnu – Rakvere - Sõmeru km 4,1 - 4,4 kergliiklustee ehitamise põhiprojekti liiklusohutuse auditeerimise II etapp ja riigitee nr 24124 Viljandi – Suure-Jaani km 10,25 – 10,50  liiklusohtliku koha likvideerimise põhiprojekti liiklusohutuse auditeerimise II etapp"}
   {:db/id 92358976735457,
    :thk.contract/procurement-id "1936",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690041,
                            :thk.activity/id "19162",
                            :task/estimated-start-date #inst"2021-02-01T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-04-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-02-05T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-3ec1-68f45ef6ce95"}],
    :thk.contract/name "Riigiteede nr 9 Ääsmäe–Haapsalu–Rohuküla km 7,3–7,7 LOK ja nr 11300 Lagedi–Aruküla–Peningi km 11,4–16,4 REK põhiprojektide LOA"}
   {:db/id 92358976735458,
    :thk.contract/procurement-id "1928",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690684,
                            :thk.activity/id "19140",
                            :task/estimated-start-date #inst"2020-11-01T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-01-24T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-01-11T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-3d26-3ba87d2b8d6a"}],
    :thk.contract/name "Riigitee 88 Rakvere-Rannapungerja km 10,256-21,304 (Rägavere-Sae lõigu) põhiprojekti liiklusohutuse auditeerimine"}
   {:db/id 92358976735459,
    :thk.contract/procurement-id "1561",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-7786-b0f146ab4d13",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "230174",
                            :activity/estimated-start-date #inst"2021-03-01T00:00:00.000-00:00",
                            :thk.activity/id "5479",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"3058498.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1561",
                            :activity/estimated-end-date #inst"2021-11-01T00:00:00.000-00:00",
                            :db/id 74766790689764,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 61 Põlva - Reola, km 30.5-37.1, rekonstrueerimine"}
   {:db/id 92358976735460,
    :thk.contract/procurement-id "1492",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690884,
                            :thk.activity/id "16812",
                            :task/estimated-start-date #inst"2020-10-15T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-12-31T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-11-01T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-12-02T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-0034-ac0000001214"}],
    :thk.contract/name "Loobu kõnniteede põhiprojekti liiklusohutuse auditeerimine"}
   {:db/id 92358976735461,
    :thk.contract/procurement-id "1923",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790691024,
                            :thk.activity/id "18811",
                            :task/estimated-start-date #inst"2020-09-01T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-02-14T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-12-30T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-390c-3ac96123dd42"}],
    :thk.contract/name "Riigiteede 11608 Vana-Narva mnt km 0,0-1,11 ja 11607 Saha-Loo tee km 0,43-1,82 rekonstrueerimise eelprojekti LOA"}
   {:db/id 92358976735462,
    :thk.contract/procurement-id "1960",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-5842-fd220bd6bc5b",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "78999",
                            :activity/estimated-start-date #inst"2020-07-01T00:00:00.000-00:00",
                            :thk.activity/id "19121",
                            :activity/integration-info "#:activity{:contract \"false\", :cost \"100000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1960",
                            :activity/estimated-end-date #inst"2021-02-26T00:00:00.000-00:00",
                            :db/id 74766790690623,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 0 Määramata(LED valgustite paigaldus), km 0.0-0.0katsetan reavahetust",
    :thk.contract/part-name "leping1",
    :thk.contract/procurement-part-id "1"}
   {:db/id 92358976735463,
    :thk.contract/procurement-id "1458",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690432,
                            :thk.activity/id "16522",
                            :task/estimated-start-date #inst"2020-08-15T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-03-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-08-12T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2021-02-08T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-00bf-e00000000844"}
                           {:db/id 74766790690433,
                            :thk.activity/id "16521",
                            :task/estimated-start-date #inst"2020-08-15T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-03-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-08-12T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2021-02-08T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-00eb-2c0000000840"}
                           {:db/id 74766790690953,
                            :thk.activity/id "16524",
                            :task/estimated-start-date #inst"2020-08-15T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-03-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-08-12T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2021-02-08T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-006f-5c0000000845"}
                           {:db/id 74766790690954,
                            :thk.activity/id "16523",
                            :task/estimated-start-date #inst"2020-08-15T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-03-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-08-12T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2021-02-08T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-0026-140000000841"}],
    :thk.contract/name "Onga ja Jõemõisa sildade põhiprojektide ekspertiis ja liiklusohutuse auditeerimine"}
   {:db/id 92358976735464,
    :thk.contract/procurement-id "1100",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-1807-758ed29e7846",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "216016",
                            :activity/estimated-start-date #inst"2019-10-01T00:00:00.000-00:00",
                            :thk.activity/id "845",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"2660561.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1100",
                            :activity/estimated-end-date #inst"2021-01-01T00:00:00.000-00:00",
                            :db/id 74766790690538,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 51 Viljandi-Põltsamaa km 35,809-43,109 Võisiku-Kuningamäe lõigu rekonstrueerimine"}
   {:db/id 92358976735465,
    :thk.contract/procurement-id "1931",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690738,
                            :thk.activity/id "19161",
                            :task/estimated-start-date #inst"2021-02-09T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-03-15T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-03-01T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-0f7d-297b962cefce"}
                           {:db/id 74766790690739,
                            :thk.activity/id "19160",
                            :task/estimated-start-date #inst"2021-02-09T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-03-15T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-03-01T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-01e0-e6c7a976b25a"}],
    :thk.contract/name "Riigitee nr 19276 Taali – Põlendmaa - Seljametsa km 6,300 asuva Vabrikuküla sillauue silla ehituse põhiprojekti liiklusohutusauditi, ekspertiisi teostamine"}
   {:db/id 92358976735466,
    :thk.contract/procurement-id "1569",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-7545-69bc7e97cab4",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "229786",
                            :activity/estimated-start-date #inst"2021-03-01T00:00:00.000-00:00",
                            :thk.activity/id "3869",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"14400000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1569",
                            :activity/estimated-end-date #inst"2022-12-31T00:00:00.000-00:00",
                            :db/id 74766790690074,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 2 Tallinn- Tartu- Võru- Luhamaa (Kärevere-Kardla), km  170.0- 174.4, tee ehitus"}
   {:db/id 92358976735467,
    :thk.contract/procurement-id "1925",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-33fd-f48e5846e382",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "232948",
                            :activity/estimated-start-date #inst"2021-04-01T00:00:00.000-00:00",
                            :thk.activity/id "18691",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"130800.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1925",
                            :activity/estimated-end-date #inst"2021-10-30T00:00:00.000-00:00",
                            :db/id 74766790689973,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee nr 1 Tallinn-Narva Kuusalu tankla juurdepääsude projekteerimine-ehitamine"}
   {:db/id 92358976735468,
    :thk.contract/procurement-id "1459",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690639,
                            :thk.activity/id "16526",
                            :task/estimated-start-date #inst"2020-08-15T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-12-31T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-08-12T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-11-23T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-008c-980000000842"}
                           {:db/id 74766790690640,
                            :thk.activity/id "16525",
                            :task/estimated-start-date #inst"2020-08-15T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-12-31T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-08-12T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-11-23T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-0057-b80000000843"}],
    :thk.contract/name "Piibe silla põhiprojekti ekspertiis ja liiklusohutuse auditeerimine"}
   {:db/id 92358976735469,
    :thk.contract/procurement-id "1592",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690521,
                            :thk.activity/id "18679",
                            :task/estimated-start-date #inst"2020-11-03T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-12-11T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-11-20T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-12-11T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-54d4-65430446641d"}],
    :thk.contract/name "Liiklusohutuse auditeerimine riigitee nr 12132 Emmaste-Tohvri km 0,0-0,1 põhiprojekt"}
   {:db/id 92358976735470,
    :thk.contract/procurement-id "1964",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-3ca6-2ddc9bcea16e",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "345666",
                            :activity/estimated-start-date #inst"2024-04-01T00:00:00.000-00:00",
                            :thk.activity/id "19228",
                            :activity/integration-info "#:activity{:contract \"false\", :cost \"3500000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1964",
                            :activity/estimated-end-date #inst"2024-10-31T00:00:00.000-00:00",
                            :db/id 74766790689768,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 3 Jõhvi - Tartu - Valga, km  143.9- 147.4, 2 Tallinn - Tartu - Võru - Luhamaa, km  195.0- 195.8, 1 Tallinn - Narva, km  118.6- 119.3, liiklusohtliku koha kõrvaldamine, Ristmiku ümber ehitamine, Kergliiklustee rajamine või korrastamine, Tunnel",
    :thk.contract/part-name "leping2",
    :thk.contract/procurement-part-id "6"}
   {:db/id 92358976735471,
    :thk.contract/procurement-id "1943",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790689854,
                            :thk.activity/id "19196",
                            :task/estimated-start-date #inst"2021-02-02T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-04-07T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-02-15T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2021-03-10T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-1bc7-4c3b320c230a"}
                           {:db/id 74766790690457,
                            :thk.activity/id "19198",
                            :task/estimated-start-date #inst"2021-02-01T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-04-07T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-02-15T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2021-03-10T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-7019-e29db85f22a0"}
                           {:db/id 74766790690814,
                            :thk.activity/id "19197",
                            :task/estimated-start-date #inst"2020-12-02T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-04-07T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-02-15T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2021-03-10T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-58e6-364741d13481"}],
    :thk.contract/name "Riigiteede põhiprojektide liiklusohutuse auditeerimine (T-11 Veneküla parkla, T-11260 LOKid)"}
   {:db/id 92358976735472,
    :thk.contract/procurement-id "1541",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690019,
                            :thk.activity/id "18244",
                            :task/estimated-start-date #inst"2020-10-20T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-10-31T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-10-14T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-10-31T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-4068-05af516b7209"}
                           {:db/id 74766790690020,
                            :thk.activity/id "18243",
                            :task/estimated-start-date #inst"2020-10-20T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-10-31T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-10-14T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-10-31T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-2359-f3a5c02d1ff4"}],
    :thk.contract/name "Riigitee nr 24110 Venevere – Tääksi km 6,879 Mädaoja silla ( nr 630 ) ehituse põhiprojekti liiklusohutusauditi ja ekspertiisi teostamine"}
   {:db/id 92358976735473,
    :thk.contract/procurement-id "42",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-2231-4bff81ba855d",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "199343",
                            :activity/estimated-start-date #inst"2019-03-01T00:00:00.000-00:00",
                            :thk.activity/id "4900",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"11026309.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "42",
                            :activity/estimated-end-date #inst"2021-05-08T00:00:00.000-00:00",
                            :db/id 74766790689403,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee nr 11 Tallinna ringtee km 20,2-24,2 Luige-Saku lõigu ehitus"}
   {:db/id 92358976735474,
    :thk.contract/procurement-id "1493",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790691018,
                            :thk.activity/id "16813",
                            :task/estimated-start-date #inst"2020-11-01T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-12-31T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-11-01T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-12-02T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-00d3-dc000000120f"}],
    :thk.contract/name "Moonaküla ristmiku põhiprojekti auditeerimine"}
   {:db/id 92358976735475,
    :thk.contract/procurement-id "1167",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-4920-6324263e28c0",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "221593",
                            :activity/estimated-start-date #inst"2020-04-01T00:00:00.000-00:00",
                            :thk.activity/id "864",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"5636192.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1167",
                            :activity/estimated-end-date #inst"2021-10-31T00:00:00.000-00:00",
                            :db/id 74766790689531,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 52 Viljandi - Rõngu km 7,000 – 22,098 Nõmme – Mustla lõigu rekonstrueerimine ja Raassilla silla lammutamine ja uue silla ehitamine"}
   {:db/id 92358976735476,
    :thk.contract/procurement-id "1475",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790689794,
                            :thk.activity/id "16466",
                            :task/estimated-start-date #inst"2020-06-03T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-01-05T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-09-14T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-12-10T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-0071-3c00000007e7"}],
    :thk.contract/name "Riigitee 4464005 Vana-Narva maantee Maardu linnas asuva lõigu rekonstrueerimise eelprojekti ja põhiprojekti LOA"}
   {:db/id 92358976735477,
    :thk.contract/procurement-id "590",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-2216-a10747908da6",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "229267",
                            :activity/estimated-start-date #inst"2021-01-01T00:00:00.000-00:00",
                            :thk.activity/id "1399",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"201023.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "590",
                            :activity/estimated-end-date #inst"2021-11-30T00:00:00.000-00:00",
                            :db/id 74766790690903,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee nr 11265 Valkla-Haapse km 1,903 Valkla silla (nr 118) ehitus"}
   {:db/id 92358976735478,
    :thk.contract/procurement-id "1434",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-035c-d8acdffd5a79",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "225139",
                            :activity/estimated-start-date #inst"2020-07-01T00:00:00.000-00:00",
                            :thk.activity/id "4699",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"3960000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1434",
                            :activity/estimated-end-date #inst"2021-11-30T00:00:00.000-00:00",
                            :db/id 74766790690581,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 1 Tallinn–Narva km 184,7-187,5 Sillamäe linna lõigu ümberehitus"}
   {:db/id 92358976735479,
    :thk.contract/procurement-id "1545",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790689361,
                            :thk.activity/id "18253",
                            :task/estimated-start-date #inst"2020-10-26T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-08-16T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-06-16T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-08-16T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-539a-d0d5ff07e2be"}],
    :thk.contract/name "Riigitee 16196 Kirbla – Rumba – Vana-Vigala km 5,03 asuv Vanamõisa silla seisukorra hinnang"}
   {:db/id 92358976735480,
    :thk.contract/procurement-id "112",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-42a3-4ad56f510680",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "211352",
                            :activity/estimated-start-date #inst"2019-01-01T00:00:00.000-00:00",
                            :thk.activity/id "5828",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"201456.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "112",
                            :activity/estimated-end-date #inst"2021-01-31T00:00:00.000-00:00",
                            :db/id 74766790690325,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Tugimaantee 25 Mäeküla-Koeru-Kapu, Mäeküla-Suurpalu lõigu rekonstrueerimine"}
   {:db/id 92358976735481,
    :thk.contract/procurement-id "831",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-24e7-aca8c4fe92ff",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "225364",
                            :activity/estimated-start-date #inst"2020-04-01T00:00:00.000-00:00",
                            :thk.activity/id "7233",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"150251.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "831",
                            :activity/estimated-end-date #inst"2021-01-12T00:00:00.000-00:00",
                            :db/id 74766790690986,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Võru maakonna LOK 2020"}
   {:db/id 92358976735482,
    :thk.contract/procurement-id "1964",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-065a-102709400f9f",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "345666",
                            :activity/estimated-start-date #inst"2024-04-01T00:00:00.000-00:00",
                            :thk.activity/id "19232",
                            :activity/integration-info "#:activity{:contract \"false\", :cost \"600000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1964",
                            :activity/estimated-end-date #inst"2024-10-31T00:00:00.000-00:00",
                            :db/id 74766790690217,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}
                           {:integration/id #uuid"00000000-0000-0000-0252-02d05e01e79e",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "345666",
                            :activity/estimated-start-date #inst"2024-04-01T00:00:00.000-00:00",
                            :thk.activity/id "19230",
                            :activity/integration-info "#:activity{:contract \"false\", :cost \"500000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1964",
                            :activity/estimated-end-date #inst"2024-10-31T00:00:00.000-00:00",
                            :db/id 74766790690824,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 3 Jõhvi - Tartu - Valga, km  143.9- 147.4, 2 Tallinn - Tartu - Võru - Luhamaa, km  195.0- 195.8, 1 Tallinn - Narva, km  118.6- 119.3, liiklusohtliku koha kõrvaldamine, Ristmiku ümber ehitamine, Kergliiklustee rajamine või korrastamine, Tunnel",
    :thk.contract/part-name "leping3",
    :thk.contract/procurement-part-id "7"}
   {:db/id 92358976735483,
    :thk.contract/procurement-id "1112",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-436f-c92a090259e3",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "D_12-1/20/52215-1",
                            :activity/estimated-start-date #inst"2021-03-01T00:00:00.000-00:00",
                            :thk.activity/id "7310",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"20460.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1112",
                            :activity/estimated-end-date #inst"2021-05-31T00:00:00.000-00:00",
                            :db/id 74766790690796,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Moodulkünniste paigaldamine koos künniseid tähistavate liikluskorraldusvahenditega riigiteele nr 14 Kose - Purila km 10,7 – 10,8 ja liiklusohtliku ülekäiguraja liiklusohutuse taseme tõstmine riigiteel nr 20170 Märjamaa-Konuvere km 0,25"}
   {:db/id 92358976735484,
    :thk.contract/procurement-id "1553",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790689541,
                            :thk.activity/id "18472",
                            :task/estimated-start-date #inst"2020-10-01T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2022-10-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-09-15T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-3527-74014f38b1a3"}],
    :thk.contract/name "Riigitee 2 Tallinn-Tartu-Võru-Luhamaa km 142,0-182,0 Pikknurme-Tartu lõigu ulukiseire"}
   {:db/id 92358976735485,
    :thk.contract/procurement-id "877",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-5180-3bc77eac8de2",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "223558",
                            :activity/estimated-start-date #inst"2020-01-01T00:00:00.000-00:00",
                            :thk.activity/id "1438",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"1326000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "877",
                            :activity/estimated-end-date #inst"2021-05-31T00:00:00.000-00:00",
                            :db/id 74766790689459,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee nr 11304 Aruküla-Kostivere km 0,0-2,609 lõigu rekonstrueerimine"}
   {:db/id 92358976735486,
    :thk.contract/procurement-id "1951",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690633,
                            :thk.activity/id "16691",
                            :task/estimated-start-date #inst"2020-08-19T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-04-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-03-19T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-00e8-000000000c99"}],
    :thk.contract/name "Liiklusohutuse auditeerimine ja projekti ekspertiis riigitee 17 Keila–Haapsalu km 63,653 ristmiku piirkonna ümberehitamise põhiprojekt"}
   {:db/id 92358976735487,
    :thk.contract/procurement-id "1422",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790689430,
                            :thk.activity/id "16390",
                            :task/estimated-start-date #inst"2020-08-17T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-09-30T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-07-01T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-07-22T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-00ce-e400000004f1"}],
    :thk.contract/name "Riigitee 1 Tallinn-Narva (E20) km 150,518 Saka ristmiku ümberehituse põhiprojekti liiklusohutuse auditeerimine"}
   {:db/id 92358976735488,
    :thk.contract/procurement-id "1937",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690546,
                            :thk.activity/id "19165",
                            :task/estimated-start-date #inst"2020-03-09T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-02-11T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2021-02-04T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-5c47-dd01a247038d"}],
    :thk.contract/name "Riigitee nr 2 Tallinn-Tartu-Võru-Luhamaa km 37,05-38,0 Kuivajõe liiklussõlme bussipeatuse ja parklate projekti LOA"}
   {:db/id 92358976735489,
    :thk.contract/procurement-id "1963",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-5fab-a4df8122eb6a",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "66666",
                            :activity/estimated-start-date #inst"2022-04-01T00:00:00.000-00:00",
                            :thk.activity/id "19296",
                            :activity/integration-info "#:activity{:contract \"false\", :cost \"55000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1963",
                            :activity/estimated-end-date #inst"2022-10-31T00:00:00.000-00:00",
                            :db/id 74766790689473,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "muutsin nime ära",
    :thk.contract/part-name "leping1",
    :thk.contract/procurement-part-id "3"}
   {:db/id 92358976735490,
    :thk.contract/procurement-id "1149",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-2145-c8471a4928bf",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "221259",
                            :activity/estimated-start-date #inst"2020-01-01T00:00:00.000-00:00",
                            :thk.activity/id "969",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"213451.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1149",
                            :activity/estimated-end-date #inst"2020-10-04T00:00:00.000-00:00",
                            :db/id 74766790689962,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 67 Võru - Mõniste - Valga km 51.5-51.6 Hargla silla remont"}
   {:db/id 92358976735491,
    :thk.contract/procurement-id "1636",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690088,
                            :thk.activity/id "18817",
                            :task/estimated-start-date #inst"2020-12-01T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2021-01-13T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-11-30T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2021-01-13T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-31f8-4cfdba08a067"}],
    :thk.contract/name "Riigitee 1 Tallinn-Narva km 170,028 Toila ristmiku ja riigitee 13105 Kõrve-Toila km 0,13-5,398 rekonstrueerimise põhiprojekti liiklusohutuse auditeerimine"}
   {:db/id 92358976735492,
    :thk.contract/procurement-id "1421",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690535,
                            :thk.activity/id "16392",
                            :task/estimated-start-date #inst"2020-08-19T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-10-13T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-09-29T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-10-20T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-00d6-e00000000554"}],
    :thk.contract/name "Riigitee 15114 km 0,00-0,75 Koeru-Visusti jalgratta- ja jalgtee ehituse põhiprojekti liiklusohutuse auditeerimine"}
   {:db/id 92358976735493,
    :thk.contract/procurement-id "126",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-3ea2-f0db194fbb04",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "203928",
                            :activity/estimated-start-date #inst"2019-04-01T00:00:00.000-00:00",
                            :thk.activity/id "76",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"15751187.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "126",
                            :activity/estimated-end-date #inst"2021-06-14T00:00:00.000-00:00",
                            :db/id 74766790689341,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Põhimaantee 1 (E20) Tallinn–Narva, Rõmeda–Haljala lõigu ehitus"}
   {:db/id 92358976735494,
    :thk.contract/procurement-id "1856",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-07ea-b757736ed6a8",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "5678888",
                            :activity/estimated-start-date #inst"2021-01-04T00:00:00.000-00:00",
                            :thk.activity/id "8283",
                            :activity/integration-info "#:activity{:contract \"false\", :cost \"400000.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1856",
                            :activity/estimated-end-date #inst"2021-08-31T00:00:00.000-00:00",
                            :db/id 74766790690192,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 16134 Oru - Soolu - Jalukse km 2.6-6.6 kruusateele katte ehitamine reavahetus",
    :thk.contract/part-name "leping1",
    :thk.contract/procurement-part-id "2"}
   {:db/id 92358976735495,
    :thk.contract/procurement-id "1424",
    :thk.contract/type #:db{:id 92358976733624, :ident :thk.contract.type/services},
    :thk.contract/targets [{:db/id 74766790690937,
                            :thk.activity/id "16396",
                            :task/estimated-start-date #inst"2020-08-31T00:00:00.000-00:00",
                            :task/estimated-end-date #inst"2020-12-21T00:00:00.000-00:00",
                            :task/actual-start-date #inst"2020-12-01T00:00:00.000-00:00",
                            :task/actual-end-date #inst"2020-12-17T00:00:00.000-00:00",
                            :integration/id #uuid"00000000-0000-0000-0009-4c0000000575"}],
    :thk.contract/name "Riigitee 17152 Vohnja-Kadrina km 2,75-3,00 ümberehituse põhiprojekti liiklusohutuse auditeerimine"}
   {:db/id 92358976735496,
    :thk.contract/procurement-id "1073",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-4835-de997334f8ae",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "219942",
                            :activity/estimated-start-date #inst"2020-03-01T00:00:00.000-00:00",
                            :thk.activity/id "4276",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"761782.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1073",
                            :activity/estimated-end-date #inst"2021-04-30T00:00:00.000-00:00",
                            :db/id 74766790690233,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee 14143 Õuna-Mutso km 3,07 Jõgeva alevikus asuva Suursilla (nr 502) rekonstrueerimine"}
   {:db/id 92358976735497,
    :thk.contract/procurement-id "1287",
    :thk.contract/type #:db{:id 92358976733622, :ident :thk.contract.type/construction-works},
    :thk.contract/targets [{:integration/id #uuid"00000000-0000-0000-457d-734f06a6dec4",
                            :activity/status #:db{:id 96757023244477, :ident :activity.status/in-progress},
                            :activity/procurement-nr "228197",
                            :activity/estimated-start-date #inst"2021-01-01T00:00:00.000-00:00",
                            :thk.activity/id "4268",
                            :activity/integration-info "#:activity{:contract \"true\", :cost \"125964.00\", :shortname \"Teostus\", :statusname \"Töös\"}",
                            :activity/procurement-id "1287",
                            :activity/estimated-end-date #inst"2021-11-30T00:00:00.000-00:00",
                            :db/id 74766790689393,
                            :activity/name #:db{:id 96757023244470, :ident :activity.name/construction}}],
    :thk.contract/name "Riigitee nr 11124 Viskla-Pikavere km 8,9 Mallavere silla (nr 88) rekonstrueerimise tööprojekti koostamine ja ehitamine"}])


(defmulti contract-search-clause (fn [[attribute _value] _user]
                                   attribute))

(defmethod contract-search-clause :shortcut
  [[_ value] user]
  (case value
    :all-contracts
    {:where '[]}
    :my-contracts
    {:where '[[?c :thk.contract/targets ?target]
              (or-join [?target ?current-user]
                       [?target :activity/manager ?current-user]
                       (and [?a :activity/tasks ?target]
                            [?a :activity/manager ?current-user]))]
     :in {'?current-user (:db/id user)}}
    :unassigned
    {:where '[(contract-target-activity ?c ?activity)
              [(missing? $ ?activity :activity/manager)]]}))

(defmethod contract-search-clause :project-name
  [[_ value] _]
  {:where '[(contract-target-project ?c ?project)
            (project-name-matches? ?project ?project-name-search-value)]
   :in {'?project-name-search-value value}})

(defmethod contract-search-clause :road-number
  [[_ value] _]
  {:where '[(contract-target-project ?c ?project)
            [?project :thk.project/road-nr ?road-number]
            [(teet.util.string/contains-words? ?road-number ?road-number-search-value)]]
   :in {'?road-number-search-value value}})

(defmethod contract-search-clause :contract-name
  [[_ value] _]
  {:where '[[?c :thk.contract/name ?thk-contract-name]
            [(get-else $ ?c :thk.contract/part-name ?thk-contract-name) ?contract-name]
            [(.toLowerCase ^String ?contract-name) ?lower-contract-name]
            [(teet.util.string/contains-words? ?contract-name ?contract-name-search-value)]]
   :in {'?contract-name-search-value (str/lower-case value)}})

(defmethod contract-search-clause :procurement-id
  [[_ value] _]
  {:where '[[?c :thk.contract/procurement-id ?proc-id]
            [(get-else $ ?c :thk.contract/procurement-part-id ?proc-id) ?procurement-id]
            [(teet.util.string/contains-words? ?procurement-id ?procurement-id-search-value)]]
   :in {'?procurement-id-search-value value}})

(defmethod contract-search-clause :procurement-number
  [[_ value] _]
  {:where '[[?c :thk.contract/procurement-number ?proc-number]
            [(teet.util.string/contains-words? ?proc-number ?proc-number-search-value)]]
   :in {'?proc-number-search-value value}})

(defmethod contract-search-clause :ta/region
  [[_ value] _]
  {:where '[[?c :ta/region ?region-search-value]]
   :in {'?region-search-value value}})

(defmethod contract-search-clause :contract-type
  [[_ value] _]
  {:where '[[?c :thk.contract/type ?contract-type-search-value]]
   :in {'?contract-type-search-value value}})

(def rules
  '[[(project-name-matches? ?project ?name-search-value)
     [?project :thk.project/name ?thk-name]
     [(get-else $ ?project :thk.project/project-name ?thk-name) ?project-name]
     [(.toLowerCase ^String ?project-name) ?lower-project-name]
     [(teet.util.string/contains-words? ?lower-project-name ?name-search-value)]]

    [(contract-target-activity ?c ?activity)
     [?c :thk.contract/targets ?activity]
     [?activity :activity/name _]]

    [(contract-target-activity ?c ?activity)
     [?c :thk.contract/targets ?target]
     [?activity :activity/tasks ?target]]

    [(contract-target-project ?c ?project)
     [?c :thk.contract/targets ?target]
     (or-join [?target ?project]
              (and
                [?activity :activity/tasks ?target]
                [?lc :thk.lifecycle/activities ?activity]
                [?project :thk.project/lifecycles ?lc])
              (and
                [?lc :thk.lifecycle/activities ?target]
                [?project :thk.project/lifecycles ?lc]))]])

(defn contract-listing-query
  "takes search params and forms datomic query with the help of contract-search multimethod"
  [db user search-params]
  (let [{:keys [where in]}
        (reduce
          (fn [accumulator attribute]
            (let [{:keys [where in]} (contract-search-clause attribute user)]
              (-> accumulator
                  (update :where concat where)
                  (update :in merge in))))
          {:where '[]
           :in {}}
          search-params)
        arglist (seq in)
        in (into '[$ %] (map first) arglist)
        args (into [db rules] (map second) arglist)]
    (->> (d/q {:query {:find '[(pull ?c [* {:thk.contract/targets [*]}])]
                       :where (into '[[?c :thk.contract/procurement-id _]]
                                    where)
                       :in in}
               :args args})
         (mapv first))))

(defquery :contract/list-contracts
  {:doc "Return a list of contracts matching given search params"
   :context {db :db user :user}
   :args {search-params :search-params}
   :project-id nil
   :authorization {}}
  (contract-listing-query db user (cu/without-empty-vals search-params)))
