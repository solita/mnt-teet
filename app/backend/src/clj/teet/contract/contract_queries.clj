(ns teet.contract.contract-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.environment :as environment]
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
        contract-targets (contract-db/contract-target-information db contract-eid)
        project-id (get-in (first contract-targets) [:project :thk.project/id])
        related-contracts (contract-db/contract-related-contracts db contract-eid [:thk.project/id project-id])
        result (-> (contract-db/get-contract
                     db
                     contract-eid)
                   (assoc :thk.contract/targets contract-targets)
                   (assoc :related-contracts related-contracts)
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
        result (-> (contract-db/get-contract-with-partners
                     db
                     contract-eid)
                 (assoc
                   :thk.contract/targets
                   (contract-db/contract-target-information db contract-eid))
                 contract-model/db-values->frontend)]
    result))

(defquery :contract/possible-partner-employees
  {:doc "Return only the users who are not added to the contract-company already"
   :context {db :db user :user}
   :args {company-contract-id :company-contract-id
          search :search}
   :project-id nil
   :authorization {:contracts/contract-editing {}}}
  (contract-db/available-company-contract-employees db company-contract-id search))

(defquery :contract/responsibilities-page
  {:doc "Returns contracts persons responsibilities"
   :context {db :db user :user}
   :args {contract-ids :contract-ids}
   :project-id nil
   :authorization {}}
  (let [[contract-id contract-part-id] contract-ids
        contract-eid [:thk.contract/procurement-id+procurement-part-id [contract-id contract-part-id]]
        targets (contract-db/contract-responsible-target-entities db contract-eid)
        partner-representatives (contract-db/contract-partner-representatives db contract-eid)
        result (-> (contract-db/get-contract db contract-eid)
                   (assoc
                    :thk.contract/targets targets
                    :partner-representatives partner-representatives)
                   contract-model/db-values->frontend)]
    result))
