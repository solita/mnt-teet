(ns teet.contract.contract-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.contract.contract-db :as contract-db]
            [teet.contract.contract-model :as contract-model]
            [teet.util.datomic :as du]))

(defquery :contract/contract-page
  {:doc "Return the single contracts information"
   :context {db :db user :user}
   :args {contract-ids :contract-ids}
   :allowed-for-all-users? true}
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
   :allowed-for-all-users? true}
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
   :args {company-contract-eid :company-contract-id
          search :search}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:action :contracts/add-existing-teet-users-to-contract
                            :company (get-in
                                       (du/entity db company-contract-eid)
                                       [:company-contract/company :db/id])}}
  (contract-db/available-company-contract-employees db company-contract-eid search))
