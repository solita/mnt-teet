(ns teet.contract.contract-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.contract.contract-db :as contract-db]
            [teet.util.datomic :as du]
            [teet.util.collection :as cu]))


(defquery :contract/contract-page
  {:doc "Return the single contracts information"
   :context {db :db user :user}
   :args {contract-ids :contract-ids}
   :project-id nil
   :authorization {}}
  (let [[contract-id contract-part-id] contract-ids
        contract-eid [:thk.contract/procurement-id+procurement-part-id [contract-id contract-part-id]]
        result (-> (contract-db/get-contract
              db
              contract-eid)
            (assoc
              :thk.contract/targets
              (contract-db/get-contract-target-information db contract-eid))
            (cu/update-in-if-exists [:thk.contract/cost] str)
            (cu/update-in-if-exists [:thk.contract/warranty-period] str)
            du/idents->keywords)]
     result))
