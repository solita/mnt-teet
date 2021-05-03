(ns teet.contract.contract-model
  (:require #?(:clj [clj-time.coerce :as tc]
               :cljs [cljs-time.coerce :as tc])))

(def contract-form-keys
  [:db/id
   :ta/region
   :thk.contract/number
   :thk.contract/external-link
   :thk.contract/signed-at
   :thk.contract/start-of-work
   :thk.contract/deadline
   :thk.contract/extended-deadline
   :thk.contract/warranty-period
   :thk.contract/cost])

(def contract-statuses
  [:thk.contract.status/signed
   :thk.contract.status/in-progress
   :thk.contract.status/deadline-approaching
   :thk.contract.status/deadline-overdue
   :thk.contract.status/warranty
   :thk.contract.status/completed])

(def contract-status-order
  {1 :thk.contract.status/signed
   2 :thk.contract.status/in-progress
   3 :thk.contract.status/deadline-approaching
   4 :thk.contract.status/deadline-overdue
   5 :thk.contract.status/warranty
   6 :thk.contract.status/completed})
