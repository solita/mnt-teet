(ns ^:db teet.asset.asset-commands-test
  (:require [teet.test.utils :as tu]
            teet.asset.asset-commands
            [clojure.test :refer [deftest is testing] :as t]
            [teet.asset.asset-model :as asset-model]
            [teet.util.datomic :as du]))

(t/use-fixtures :each
  tu/with-environment
  (tu/with-config {:asset {:default-owner-code "TST"}
                   :enabled-features #{:asset-db :cost-items}})
  (tu/with-db)
  tu/with-global-data)

(deftest locking-denies-edits
  (tu/local-login tu/mock-user-boss)
  (testing "Initially assets can be created"
    (let [{oid :asset/oid :as resp}
          (tu/local-command :asset/save-cost-item
                            {:project-id "11111"
                             :asset {:db/id "bridge"
                                     :asset/fclass :fclass/bridge}})]
      (println "SAVE COST ITEM RESPONSE: " (pr-str resp))
      (is (asset-model/asset-oid? oid))))

  (testing "Locking creates new lock entity"
    (let [now (java.util.Date.)
          id (get-in (tu/local-command
                      :asset/lock-version
                      {:boq-version/type :boq-version.type/budget
                       :boq-version/project "11111"})
                     [:tempids "lock"])
          lock (du/entity (tu/adb) id)]
      (is (= 1 (:boq-version/number lock)))
      (is (<= (.getTime now) (.getTime (:boq-version/created-at lock))))))

  (testing "Saving new asset throws error"
    (tu/is-thrown-with-data?
     {:error :boq-is-locked}
     (tu/local-command :asset/save-cost-item
                            {:project-id "11111"
                             :asset {:db/id "bridge"
                                     :asset/fclass :fclass/bridge}}))))
