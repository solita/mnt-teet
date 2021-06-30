(ns teet.contract.contract-commands
  (:require [teet.db-api.core :refer [defcommand tx]]
            [teet.meta.meta-model :as meta-model]
            [teet.contract.contract-model :as contract-model]
            [teet.util.datomic :as du]
            [teet.company.company-db :as company-db]
            [teet.company.company-model :as company-model]
            [teet.contract.contract-db :as contract-db]
            [clojure.string :as str])
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
  (let [company-fields (-> (select-keys form-data [:company/country
                                                   :company/business-registry-code :company/name
                                                   :company/email :company/phone-number])
                           (assoc :company/business-registry-code
                                  (company-model/company-business-registry-id-with-country-code form-data)))
        contract-eid (:db/id contract)
        lead-partner? (:company-contract/lead-partner? form-data)
        new-company-contract-id (UUID/randomUUID)
        new-company-id "new-company"
        tempids
        (:tempids (tx [(list 'teet.contract.contract-tx/update-contract-partner
                             contract-eid
                             lead-partner?
                             new-company-id
                             [(merge
                                {:db/id new-company-id
                                 :teet/id (UUID/randomUUID)}
                                company-fields
                                (meta-model/creation-meta user))
                              (merge
                                {:db/id "new-company-contract"
                                 :company-contract/company new-company-id
                                 :company-contract/contract contract-eid
                                 :teet/id new-company-contract-id}
                                (when lead-partner?
                                  {:company-contract/lead-partner? true})
                                (meta-model/creation-meta user))])]))]
     (merge tempids
            {:company-contract-id new-company-contract-id})))

(defcommand :thk.contract/edit-contract-partner-company
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
  (let [company-fields (-> (select-keys
                             form-data [:db/id
                                        :company/country
                                        :company/business-registry-code
                                        :company/name
                                        :company/email
                                        :company/phone-number
                                        :teet/id]))
        company-id (:db/id form-data)
        contract-eid (:db/id contract)
        company-contract-uuid (contract-db/contract-partner-relation-entity-uuid db company-id contract-eid)
        lead-partner? (:company-contract/lead-partner? form-data)
        tempids
        (:tempids (tx [(list 'teet.contract.contract-tx/update-contract-partner
                         contract-eid
                         lead-partner?
                         company-id
                         [(merge
                            company-fields
                            (meta-model/modification-meta user))
                          (merge
                            {:teet/id company-contract-uuid
                             :company-contract/lead-partner? (if (true? lead-partner?) true false)}
                            (meta-model/modification-meta user))])]))]
    tempids))

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
        tempids (:tempids (tx [(list 'teet.contract.contract-tx/update-contract-partner
                                     contract-eid
                                     lead-partner?
                                     company-id
                                     [(merge {:db/id "new-company-contract"
                                              :teet/id new-company-contract-id
                                              :company-contract/company company-id
                                              :company-contract/contract contract-eid}
                                             (meta-model/creation-meta user)
                                             (when lead-partner?
                                               {:company-contract/lead-partner? true}))])]))]
    (merge tempids
           {:company-contract-id new-company-contract-id})))

(defcommand :thk.contract/add-contract-employee
  {:doc "Add an existing user as a member in a given contract-company"
   :payload {form-value :form-value
             company-contract-eid :company-contract-eid}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :pre [(not
           (contract-db/is-company-contract-employee?
             db company-contract-eid
             (get-in form-value [:company-contract-employee/user :db/id])))]
   :transact [(merge
                {:db/id "new-company-contract-employee"
                 :company-contract-employee/user (:company-contract-employee/user form-value)
                 :company-contract-employee/role (mapv
                                                   :db/id
                                                   (:company-contract-employee/role form-value))}
                (meta-model/creation-meta user))
              {:db/id company-contract-eid
               :company-contract/employees "new-company-contract-employee"}]})

