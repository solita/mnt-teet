(ns teet.contract.contract-commands
  (:require [teet.db-api.core :refer [defcommand]]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as cu]
            [teet.contract.contract-model :as contract-model]
            [teet.util.datomic :as du]
            [teet.util.coerce :refer [->long ->bigdec]]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]))

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

(defcommand :thk.contract/edit-contract-details
  {:doc "Form save command for contract detail editing"
   :payload {form-data :form-data :as payload}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :transact
   (let [contract-data (-> form-data
                           (select-keys contract-model/contract-form-keys)
                           (cu/update-in-if-exists [:thk.contract/cost] ->bigdec)
                           (cu/update-in-if-exists [:thk.contract/warranty-period] ->long)
                           contract-with-warranty-end-date
                           (merge (meta-model/modification-meta user)))]
     (du/modify-entity-retract-nils db contract-data))})
