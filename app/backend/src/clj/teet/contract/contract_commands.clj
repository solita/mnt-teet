(ns teet.contract.contract-commands
  (:require [teet.db-api.core :refer [defcommand]]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as cu]
            [teet.contract.contract-model :as contract-model]
            [teet.util.datomic :as du]
            [teet.util.coerce :refer [->long]]))

(defcommand :thk.contract/edit-contract-details
  {:doc "Form save command for contract detail editing"
   :payload {form-data :form-data :as payload}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :transact
   (let [contract-data (-> form-data
                           (select-keys contract-model/contract-form-keys)
                           (cu/update-in-if-exists [:thk.contract/cost] bigdec)
                           (cu/update-in-if-exists [:thk.contract/warranty-period] ->long)
                           (merge (meta-model/modification-meta user)))]
     (du/modify-entity-retract-nils db contract-data))})
