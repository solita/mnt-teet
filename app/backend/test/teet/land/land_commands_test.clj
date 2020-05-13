(ns teet.land.land-commands-test
  (:require [clojure.test :refer :all]
            [teet.land.land-commands :as land-commands]))

(deftest estate-procedure-key-parsing
  (let [test-data {:estate-procedure/compensations
                   [#:estate-compensation{:reason :estate-compensation.reason/forest,
                                          :amount "123"}],
                   :estate-procedure/third-party-compensations
                   [#:estate-compensation{:description "123", :amount "123"}],
                   :estate-procedure/land-exchanges
                   [#:land-exchange{:cadastral-unit "123",
                                    :area "123",
                                    :price-per-sqm "123",
                                    :cadastral-unit-id "asd123"}],
                   :estate-procedure/pos "2236",
                   :estate-procedure/type :estate-procedure.type/property-rights,
                   :thk.project/id "14643",
                   :estate-procedure/estate-id "2644340"}
        parsed-data (land-commands/estate-procedure-tx test-data)]
    (testing "Proper keys are dropped"
      (is (not (contains? parsed-data :estate-procedure/land-exchanges))))
    (testing "Proper keys are parsed"
      (is (decimal? (get-in parsed-data [:estate-procedure/compensations 0 :estate-compensation/amount])))
      (is (int? (:estate-procedure/pos parsed-data))))))
