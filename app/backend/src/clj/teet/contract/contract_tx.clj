(ns teet.contract.contract-tx
  (:require [teet.contract.contract-db :as contract-db]))

(defn update-contract-partner
  "Updates to contract partners should be ran through this tx function
  to make sure we always have only 1 lead partner per contract"
  [db contract-id lead-partner? company-id tx-data]
  (if-not lead-partner?
    tx-data
    (let [[current-lead-company contract-company] (contract-db/contract-lead-partner-entities db contract-id)
          retract-previous-lead-partner? (and current-lead-company
                                              (not= current-lead-company company-id))
          lead-partner-retraction
          (if retract-previous-lead-partner?
            [[:db/retract contract-company :company-contract/lead-partner? true]]
            [])]
      (into tx-data lead-partner-retraction))))
