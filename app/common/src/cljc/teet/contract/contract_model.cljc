(ns teet.contract.contract-model
  (:require [clojure.string :as str]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            #?(:clj [teet.util.coerce :refer [->long ->bigdec]])
            #?(:clj [clj-time.core :as t]
               :cljs [cljs-time.core :as t])
            #?(:clj [clj-time.coerce :as tc]
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

(defn contract-url-id
  [{:thk.contract/keys [procurement-id procurement-part-id]}]
  (str/join "-" (filterv some? [procurement-id procurement-part-id])))

(defn contract-with-warranty-end-date
  "Calculates contracts warranty end date given the proper values if not possible assoc nil in warrant-end-date"
  [{:thk.contract/keys [warranty-period deadline extended-deadline] :as contract}]
  (let [act-dl (or extended-deadline deadline)
        warranty-end-date (when (and act-dl warranty-period)
                            (-> act-dl
                                tc/from-date
                                (t/plus (t/months warranty-period))
                                tc/to-date))]
    (assoc contract :thk.contract/warranty-end-date warranty-end-date)))

(defn db-values->frontend
  [contract-form-data]
  (-> contract-form-data
      (cu/update-in-if-exists [:thk.contract/cost] str)
      (cu/update-in-if-exists [:thk.contract/warranty-period] str)
      du/idents->keywords))

#?(:clj
   (defn form-values->db-values
     [contract]
     (-> contract
         (select-keys contract-form-keys)
         (cu/update-in-if-exists [:thk.contract/cost] ->bigdec)
         (cu/update-in-if-exists [:thk.contract/warranty-period] ->long)
         contract-with-warranty-end-date)))
