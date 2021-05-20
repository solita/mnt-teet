(ns teet.contract.contract-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.contract.contract-db :as contract-db]
            [teet.contract.contract-model :as contract-model]))

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
                     (contract-db/contract-target-information db contract-eid))
                   contract-model/db-values->frontend)]
     result))

(defquery :contract/partner-page
  {:doc "Return contract partners information"
   :context {db :db user :user}
   :args {contract-ids :contract-ids}
   :project-id nil
   :authorization {}}
  (let [[contract-id contract-part-id] contract-ids
        contract-eid [:thk.contract/procurement-id+procurement-part-id [contract-id contract-part-id]]
        ;; TODO add partners info query to result
        result (-> (contract-db/get-contract
                     db
                     contract-eid)
                 (assoc
                   :thk.contract/targets
                   (contract-db/contract-target-information db contract-eid))
                 contract-model/db-values->frontend)]
    result))
