(ns teet.migration.activate-contract-employees
  (:require [datomic.client.api :as d]))

(defn mark-all-contract-company-employees-as-active
  [conn]
  (let [contract-company-employees (d/q '[:find ?cce
                                          :where
                                          [?cce :company-contract-employee/user _]]
                                        (d/db conn))]
    (d/transact
      conn
      {:tx-data (vec (for [[cce-id] contract-company-employees]
                       {:db/id cce-id
                        :company-contract-employee/active? true}))})))

