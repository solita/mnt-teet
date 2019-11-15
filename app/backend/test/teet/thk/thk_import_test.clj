(ns teet.thk.thk-import-test
  (:require [clojure.test :refer :all]
            [teet.thk.thk-import :as thk-import]))

(def test-row
  {"PlanObject.Carriageway" "1"
   "Activity.Id" "230498"
   "PlanObject.countyfk" "2348230498"
   "ObjectType.ObjectTypeFK" ""
   "PlanObject.BridgeNr" "12345"
   "ActivityFinance.FinanceFK" "IN234234098"
   "ActivityCost.ActualCost" "0"
   "Procurement.ProcurementNo" "2309852095"
   "tegevuse andmete muutmiskuupäev mini-TIS'is" ""
   "Activity.EstStart" "2019-02-04"
   "Activity.Contract" "true"
   "ActivityCost.EstCost" "0"
   "Activity.ActualEnd" ""
   "Activity.ActivityTypeFK" "ojv"
   "ActivityFinance.Id" "230498"
   "Activity.EstEnd" "2020-07-01"
   "Activiy.UpdStamp" "2019-09-27 10:34:00"
   "PlanObject.RoadNr" "580934893"
   "PlanObject.ObjectName" "Tallinn - Helsinki"
   "Procurement.ID" "999"
   "ActivityCost.AdjustedCost" "0"
   "ActivityCost.FinanceYear" "2019"
   "PlanObject.UpdStamp" "2019-09-16 14:10:00"
   "PlanObject.PlanGroupFK" "FOOGROUP"
   "Activity.FinishedProcurement" "true"
   "Activity.GuaranteeExpired" ""
   "maksumuste muutmiskuupäev mini-TIS'is" ""
   "PlanObject.regionfk" "9999"
   "PlanObject.Id" "1234"
   "ActivityCost.UpdStamp" "2019-10-07 08:45:00"
   "PlanObject.KmStart" "0.100"
   "ActivityFinance.RefundCodeFK" ""
   "PlanObject.CustomerUnit" ""
   "PlanObject.OperMethod" ""
   "Activity.ActualStart" "2019-05-21"
   "PlanObject.investobjecttypefk" ""
   "ActivityFinance.UpdStamp" "2018-12-03 15:30:00"
   "PlanObject.KmEnd" "99.999"})

(deftest project-datomic-attributes
  (testing "nil and nonexistent values are left out"
    (is (= (thk-import/project-datomic-attributes {"PlanObject.Id" "some-id"
                                                   "PlanObject.ObjectName" nil})
           {:thk.project/id "some-id"})))
  #_(testing "all the necessary attributes are obtained"
    (is (= (thk-import/project-datomic-attributes test-row)
           #:thk.project{:id "1234"
                         :road-nr 580934893
                         :bridge-nr 12345

                         :start-m 100
                         :end-m 99999
                         :carriageway 1

                         :name "Tallinn - Helsinki"

                         :procurement-nr "2309852095"
                         :procurement-id 999

                         :estimated-start-date #inst "2019-02-03T22:00:00.000-00:00"
                         :estimated-end-date #inst "2020-06-30T21:00:00.000-00:00"}))))

(deftest teet-project?
  (testing "TEET project needs to have an associated road object"
    (is (not (thk-import/teet-project? (assoc test-row "PlanObject.KmStart" "")))))
  (testing "Not all project types are TEET projects"
    (doseq [project-type thk-import/excluded-project-types]
      (is (not (thk-import/teet-project? (assoc test-row "PlanObject.PlanGroupFK" project-type))))))
  (testing "Otherwise, you're good to go"
    (is (thk-import/teet-project? test-row))))
