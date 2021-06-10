(ns teet.contract.contract-commands
  (:require [teet.db-api.core :refer [defcommand tx]]
            [teet.meta.meta-model :as meta-model]
            [teet.contract.contract-model :as contract-model]
            [teet.util.datomic :as du]
            [teet.company.company-db :as company-db]
            [teet.company.company-model :as company-model])
  (:import (java.util UUID)))


(defcommand :thk.contract/edit-contract-details
  {:doc "Form save command for contract detail editing"
   :payload {form-data :form-data :as payload}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :transact
   (let [contract-data (-> form-data
                           contract-model/form-values->db-values
                           (merge (meta-model/modification-meta user)))]
     (du/modify-entity-retract-nils db contract-data))})

(defcommand :thk.contract/add-new-contract-partner-company
  {:doc "Save a new contract partner"
   :payload {form-data :form-data
             contract :contract}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :pre [^{:error :business-registry-code-in-use}
         (company-db/business-registry-code-unique?
           db
           (company-model/company-business-registry-id-with-country-code form-data))]}
  ;; TODO retract lead partner info if new info arrives
  (let [form-data (-> (select-keys form-data [:company/country :company/emails :company/phone-numbers
                                              :company/business-registry-code :company/name])
                      (assoc :company/business-registry-code
                             (company-model/company-business-registry-id-with-country-code form-data)))
        contract-eid (:db/id contract)
        #_#_lead-partner? (:company-contract/lead-partner? form-data)
        new-company-contract-id (UUID/randomUUID)
        tempids
        (:tempids (tx [(merge
                         {:db/id "new-company"
                          :teet/id (UUID/randomUUID)}
                         form-data
                         (meta-model/creation-meta user))]
                      [(merge
                         {:db/id "new-company-contract"
                          :company-contract/company "new-company"
                          :company-contract/contract contract-eid
                          :teet/id new-company-contract-id}
                         (meta-model/creation-meta user))]))]
     (merge tempids
            {:company-contract-id new-company-contract-id})))

(defcommand :thk.contract/add-existing-company-as-partner
  {:doc "Save an existing company as contract partner"
   :payload {contract :contract
             form-data :form-data
             :as payload}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :pre [(company-db/is-company? db (:db/id form-data))
         (not (company-db/company-in-contract? db (:db/id form-data) (:db/id contract)))]}
  (let [company-id (:db/id form-data)
        contract-eid (:db/id contract)
        lead-partner? (:company-contract/lead-partner? form-data)
        new-company-contract-id (UUID/randomUUID)
        tempids (:temids (tx [(merge {:db/id "new-company-contract"
                                      :teet/id new-company-contract-id
                                      :company-contract/company company-id
                                      :company-contract/contract contract-eid}
                                     (meta-model/creation-meta user)
                                     (when lead-partner?
                                       {:company-contract/lead-partner? true}))]))]
    (merge tempids
           {:company-contract-id new-company-contract-id})))

