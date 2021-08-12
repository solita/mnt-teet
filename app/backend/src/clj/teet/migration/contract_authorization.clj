(ns teet.migration.contract-authorization
  (:require [datomic.client.api :as d]))

(def old->new {:manager :ta-project-manager
               :internal-consultant :ta-consultant})

(defn migrate-roles [conn]
  (d/transact
   conn
   {:tx-data
    (mapv (fn [[id role]]
            {:db/id id
             :permission/role (old->new role)})

          (d/q '[:find ?e ?r
                 :where [?e :permission/role ?r]
                 :in $ [?r ...]]
               (d/db conn)
               (keys old->new)))}))
