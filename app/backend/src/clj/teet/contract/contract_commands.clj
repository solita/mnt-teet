(ns teet.contract.contract-commands
  (:require [teet.db-api.core :refer [defcommand tx]]
            [teet.meta.meta-model :as meta-model]
            [teet.contract.contract-model :as contract-model]
            [teet.user.user-db :as user-db]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.company.company-db :as company-db]
            [teet.company.company-model :as company-model]
            [teet.contract.contract-db :as contract-db]
            [teet.user.user-model :as user-model]
            [datomic.client.api :as d])
  (:import (java.util UUID)))


(defcommand :thk.contract/edit-contract-details
  {:doc "Form save command for contract detail editing"
   :payload {form-data :form-data :as payload}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:action :contract/edit-contract-metadata}
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
   :contract-authorization {:action :contract/manage-contract-companies}
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
  {:doc "Save contract partner"
   :payload {form-data :form-data
             contract :contract}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:action :contract/edit-company-information
                            :company (:db/id form-data)}
   :pre [^{:error :business-registry-code-in-use}
         (company-db/business-registry-code-unique?
           db (company-model/company-business-registry-id-with-country-code form-data))]}
  (let [company-fields (select-keys
                         form-data [:db/id
                                    :company/country
                                    :company/business-registry-code
                                    :company/name
                                    :company/email
                                    :company/phone-number
                                    :teet/id])
        company-id (:db/id form-data)
        contract-eid (:db/id contract)
        company-contract-uuid (contract-db/contract-partner-relation-entity-uuid db company-id contract-eid)
        lead-partner? (:company-contract/lead-partner? form-data)
        tempids
        (:tempids (tx [(list 'teet.contract.contract-tx/update-contract-partner
                             contract-eid
                             lead-partner?
                             company-id
                             (concat [(merge
                                        company-fields
                                        (meta-model/modification-meta user))
                                      (merge
                                        {:teet/id company-contract-uuid}
                                        (when (true? lead-partner?)
                                              {:company-contract/lead-partner? true})
                                        (meta-model/modification-meta user))]
                                     (when (not (true? lead-partner?))
                                           [[:db/retract [:teet/id company-contract-uuid]
                                             :company-contract/lead-partner? true]])))]))]
    tempids))

(defcommand :thk.contract/add-existing-company-as-partner
  {:doc "Save an existing company as contract partner"
   :payload {contract :contract
             form-data :form-data
             :as payload}
   :context {:keys [user db]}
   :project-id nil
   :contract-authorization {:action :contract/edit-contract-metadata}
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

(defcommand :thk.contract/delete-existing-company-from-contract-partners
  {:doc "Remove company from contract partners list"
   :payload {company :company
             :as payload}
   :context {:keys [user db]}
   :project-id nil
   :contract-authorization {:action :contract/edit-contract-metadata}
   :authorization {:contracts/contract-editing {}}
   :pre [(company-db/is-company? db (:db/id (:company-contract/company (:partner company))))
         (company-db/company-in-contract? db (:db/id (:contract company))
                                          (:db/id (:company-contract/company (:partner company))))]}
  (let [company-contract-id (:db/id (:partner company))
        contract-eid (:db/id (:contract company))
        company-id (:db/id (:company-contract/company (:partner company)))
        tx-data [[:db/retract company-contract-id :company-contract/company company-id]
                 [:db/retract company-contract-id :company-contract/contract contract-eid]
                 (meta-model/deletion-tx user company-contract-id)]
        tempids (:tempids (tx tx-data))]
    tempids))

(defcommand :thk.contract/add-contract-employee
  {:doc "Add an existing user as a member in a given contract-company"
   :payload {form-value :form-value
             company-contract-eid :company-contract-eid}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:action :contract/manage-contract-employees
                            :company (get-in
                                       (du/entity db company-contract-eid)
                                       [:company-contract/company :db/id])}
   :pre [(not
           (contract-db/is-company-contract-employee?
             db company-contract-eid
             [:user/person-id (get-in form-value [:company-contract-employee/user :user/person-id])]))]
   :transact [(merge
                {:db/id "new-company-contract-employee"
                 :company-contract-employee/active? true    ;; employees are active by default
                 :company-contract-employee/user (:company-contract-employee/user form-value)
                 :company-contract-employee/role (mapv
                                                   :db/id
                                                   (:company-contract-employee/role form-value))}
                (meta-model/creation-meta user))
              {:db/id company-contract-eid
               :company-contract/employees "new-company-contract-employee"}]})

(defcommand :thk.contract/change-person-status
  {:doc "Activate/Deactivate contract person"
   :payload {employee-id :employee-id
             active? :active?}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:action :contract/manage-company-employees
                            :company (get-in
                                       (du/entity db employee-id)
                                       [:company-contract/_employees :company-contract/company :db/id])}
   :transact [{:db/id employee-id
               :company-contract-employee/active? active?}]})

(defcommand :thk.contract/assign-key-person
  {:doc "Assign / un-assign key person status"
   :payload {employee-id :employee-id
             key-person? :key-person?}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/manage-company-emplyees {}}
   :contract-authorization {:action :contract/manage-company-employees
                            :company (get-in
                                       (du/entity db employee-id)
                                       [:company-contract/_employees :company-contract/company :db/id])}
   :transact
   (let [user-id (contract-db/get-user-for-company-contract-employee db employee-id)
         {user-files :user/files
          user-licenses :user/licenses} (d/pull db '[:user/files :user/licenses] user-id)]

     [(merge {:db/id employee-id
              :company-contract-employee/key-person? key-person?
              :company-contract-employee/key-person-status :key-person.status/assigned}
             (when (and key-person? (seq user-files))
               {:company-contract-employee/attached-files (mapv :db/id user-files)})
             (when (and key-person? (seq user-licenses))
               {:company-contract-employee/attached-licenses (mapv :db/id user-licenses)}))])})

(defn- form-value->person-id-eid [form-value]
  [:user/person-id (-> form-value
                       :user/person-id
                       user-model/normalize-person-id)])

(defcommand :thk.contract/add-new-contract-employee
  {:doc "Add a new contract employee"
   :payload {form-value :form-value
             company-contract-eid :company-contract-eid}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:action :contract/manage-company-employees
                            :company (get-in
                                       (du/entity db company-contract-eid)
                                       [:company-contract/company :db/id])}
   :pre [^{:error :existing-teet-user}
         (not (user-db/user-has-logged-in? db (form-value->person-id-eid form-value)))

         ^{:error :employee-already-added-to-contract}
         (not (contract-db/is-company-contract-employee? db
                                                         company-contract-eid
                                                         (form-value->person-id-eid form-value)))]}
  (let [employee-fields (-> (select-keys form-value [:user/given-name :user/family-name
                                                     :user/email :user/phone-number]))
        user-person-id (user-model/normalize-person-id (:user/person-id form-value))
        new-user (merge
                   {:db/id "new-employee-id"
                    :user/person-id user-person-id
                    :user/id (java.util.UUID/randomUUID)}
                   employee-fields
                   (meta-model/creation-meta user))
        tempids (:tempids (tx [(list 'teet.user.user-tx/ensure-unique-email
                                     (:user/email form-value)
                                     [new-user
                                      (merge
                                        {:db/id "new-company-contract-employee"
                                         :company-contract-employee/active? true ;; employees are active by default
                                         :company-contract-employee/user "new-employee-id"
                                         :company-contract-employee/role (mapv
                                                                           :db/id
                                                                           (:company-contract-employee/role form-value))}
                                        (meta-model/creation-meta user))
                                      {:db/id company-contract-eid
                                       :company-contract/employees "new-company-contract-employee"}])]))]
    tempids))

(defn- personal-information [user-info]
  (let [personal-data [:user/given-name :user/family-name :user/person-id :user/email :user/phone-number]]
    (cu/keep-vals not-empty
                  (select-keys user-info personal-data))))

(defn- personal-information-edited? [user-from-db form-value]
  (not= (personal-information user-from-db)
        (personal-information (update form-value :user/person-id user-model/normalize-person-id))))

(defn- either-user-has-not-logged-in-or-no-personal-information-edited? [db form-value]
  (when-let [edited-user (user-db/user-info db [:user/id (:user/id form-value)])]
    (or (not (:user/last-login edited-user))
        (not (personal-information-edited? edited-user form-value)))))

(defcommand :thk.contract/edit-contract-employee
  {:doc "Update existing contract employee"
   :payload {form-value :form-value
             company-contract-eid :company-contract-eid}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:action :contract/manage-company-employees
                            :company (get-in
                                       (du/entity db company-contract-eid)
                                       [:company-contract/company :db/id])}
   :pre [(either-user-has-not-logged-in-or-no-personal-information-edited? db form-value)]}
  (let [roles-update (mapv
                       #(if (not (:db/id %))
                          (:db/id (company-db/get-employee-role db %))
                          (:db/id %))
                       (:company-contract-employee/role form-value))
        old-roles (company-db/employee-roles db [:user/id (:user/id form-value)] company-contract-eid)
        employee-fields (-> (select-keys form-value [:user/given-name :user/family-name
                                                     :user/email :db/id
                                                     :user/id]))
        user-person-id (user-model/normalize-person-id (:user/person-id form-value))
        updated-user (merge
                       {:user/person-id user-person-id}
                       (if (not (nil? (:user/phone-number form-value)))
                         {:user/phone-number (:user/phone-number form-value)}
                         {:user/phone-number ""})
                       employee-fields
                       (meta-model/modification-meta user))
        company-contract-employee (company-db/find-company-contract-employee
                                    db [:user/id (:user/id employee-fields)] company-contract-eid)
        tx-data (vec
                  (concat
                    [(list 'teet.user.user-tx/ensure-unique-email
                           (:user/email form-value)
                           [updated-user
                            (merge
                              {:db/id company-contract-employee
                               :company-contract-employee/active? true
                               :company-contract-employee/user [:user/id (:user/id employee-fields)]
                               :company-contract-employee/role roles-update}
                              (meta-model/modification-meta user))])]
                    (for
                      [old-role old-roles
                       :when (not (some #(= old-role %) roles-update))]
                      [:db/retract company-contract-employee :company-contract-employee/role old-role])))
        tempids (:tempids (tx tx-data))]
    tempids))

(defcommand :thk.contract/remove-file-link
  {:doc "Remove a file link between user file and employee"
   :payload {employee-id :employee-id
             file-id :file-id}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:action :contract/manage-company-employees
                            :company {:action :contract/manage-company-employees
                                      :company (get-in
                                                 (du/entity db employee-id)
                                                 [:company-contract/_employees :company-contract/company :db/id])}}
   :transact [[:db/retract employee-id :company-contract-employee/attached-files file-id]]})

(defcommand :thk.contract/save-license
  {:doc "Save a license"
   :payload {:keys [employee-id license]}
   :context {:keys [user db]}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :pre [;; Check license is new or belongs to user when editing
         (or (not (contains? license :db/id))
             (some #(= (:db/id license)
                       (:db/id %))
                   (:user/licenses
                    (du/entity
                     db
                     (contract-db/get-user-for-company-contract-employee db employee-id)))))]
   :transact
   (let [user-id (contract-db/get-user-for-company-contract-employee db employee-id)
         license-id (:db/id license "new-license")]
     [{:db/id user-id
       :user/licenses license-id}
      {:db/id employee-id
       :company-contract-employee/attached-licenses license-id}
      (meta-model/with-creation-or-modification-meta
        user
        (merge (select-keys license
                            [:user-license/name
                             :user-license/expiration-date
                             :user-license/link])
               {:db/id license-id}))])})

(defcommand :thk.contract/submit-key-person
  {:doc "Submit key person for review"
   :payload {employee-id :employee-id}
   :project-id nil
   :authorization {:contracts/contract-editing {}}
   :contract-authorization {:contract/submit-key-person-for-approval
                            :company (get-in
                                       (du/entity db employee-id)
                                       [:company-contract/_employees :company-contract/company :db/id])}
   :transact [{:db/id employee-id
               :company-contract-employee/key-person-status :key-person.status/approval-requested}]})
