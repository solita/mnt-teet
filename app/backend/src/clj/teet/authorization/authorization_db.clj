(ns teet.authorization.authorization-db
  (:require [datomic.client.api :as d]
            [teet.contract.contract-db :as contract-db]))

(defn user-roles-for-target
  [db user target]
  (->> (d/q '[:find ?role
              :in $ ?user ?target-param
              :where
              [?cce :company-contract-employee/user ?user]
              [?cce :company-contract-employee/active? true]
              [?cc :company-contract/employees ?cce]
              [?cc :company-contract/contract ?contract]
              (or-join [?target-param ?contract]
                       [?contract :thk.contract/targets ?target-param]
                       (and
                         [?contract :thk.contract/targets ?parent]
                         [?parent :activity/tasks ?target-param]))
              [?cce :company-contract-employee/role ?r]
              [?r :db/ident ?role]]
            db user target)
       (mapv first)
       set))

(defn user-roles-for-company
  [db user company]
  (->> (d/q '[:find ?role
              :in $ ?user ?company
              :where
              [?cce :company-contract-employee/user ?user]
              [?cce :company-contract-employee/active? true]
              [?cc :company-contract/employees ?cce]
              [?cc :company-contract/company ?company]
              [?cce :company-contract-employee/role ?r]
              [?r :db/ident ?role]]
            db user company)
       (mapv first)
       set))

(defn user-roles-for-project
  [db user project-id]
  (->> (d/q '[:find ?role
              :in $ % ?user ?project
              :where
              [?cce :company-contract-employee/user ?user]
              [?cce :company-contract-employee/active? true]
              [?cc :company-contract/employees ?cce]
              [?cc :company-contract/contract ?contract]
              (contract-target-project ?contract ?project)
              [?cce :company-contract-employee/role ?r]
              [?r :db/ident ?role]]
            db
            contract-db/contract-query-rules
            user
            project-id)
       (mapv first)
       set))

(defn user-roles-for-contract
  [db user contract-id]
  (->> (d/q '[:find ?role
              :in $ ?user ?contract
              :where
              [?cce :company-contract-employee/user ?user]
              [?cce :company-contract-employee/active? true]
              [?cc :company-contract/employees ?cce]
              [?cc :company-contract/contract ?contract]
              [?cce :company-contract-employee/role ?r]
              [?r :db/ident ?role]]
            db
            user
            contract-id)
       (mapv first)
       set))
