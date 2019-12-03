(ns teet.thk.thk-import-test
  (:require [clojure.test :refer :all]
            [teet.thk.thk-import :as thk-import]))

;; TODO: Proper tets now that the imports have changed
(deftest project-datomic-attributes
  (testing "nil and nonexistent values are left out"
    (is (= (thk-import/project-datomic-attributes ["some-id" [{"phase" "ehitus"}
                                                              {"phase" "ehitus"}]])
           {:db/id                  "some-id"
            :thk.project/id         "some-id"
            :thk.project/lifecycles [{:db/id                              "some-id-ehitus"
                                      :thk.lifecycle/estimated-end-date   nil
                                      :thk.lifecycle/estimated-start-date nil
                                      :thk.lifecycle/id                   "some-id-ehitus"
                                      :thk.lifecycle/type                 :thk.lifecycle-type/design}
                                     {:db/id                              "some-id-ehitus"
                                      :thk.lifecycle/estimated-end-date   nil
                                      :thk.lifecycle/estimated-start-date nil
                                      :thk.lifecycle/id                   "some-id-ehitus"
                                      :thk.lifecycle/type                 :thk.lifecycle-type/design}]})))
  #_(testing "all the necessary attributes are obtained"
      (is (= (thk-import/project-datomic-attributes test-row)
             #:thk.project{:id                   "1234"
                           :road-nr              580934893
                           :bridge-nr            12345

                           :start-m              100
                           :end-m                99999
                           :carriageway          1

                           :name                 "Tallinn - Helsinki"

                           :procurement-nr       "2309852095"
                           :procurement-id       999

                           :estimated-start-date #inst "2019-02-03T22:00:00.000-00:00"
                           :estimated-end-date   #inst "2020-06-30T21:00:00.000-00:00"}))))
