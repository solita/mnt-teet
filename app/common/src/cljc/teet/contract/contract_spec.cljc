(ns teet.contract.contract-spec
  (:require [clojure.spec.alpha :as s]))

(s/def :thk.contract/edit-contract-details (s/keys :req [:thk.contract/number
                                                         :thk.contract/external-link
                                                         :ta/region
                                                         :thk.contract/signed-at]))

(s/def :contract-company/new-company (s/keys :req [:company/country
                                                   :company/business-registry-code
                                                   :company/name]))

(s/def :contract-company/edit-company (s/keys :req [:company/country
                                                   :company/business-registry-code
                                                   :company/name]))

(s/def :thk.contract/add-contract-employee
  (s/keys :req [(or (and :user/email
                         :user/person-id)
                    (and :company-contract-employee/user))
                :company-contract-employee/role]))

(s/def :thk.contract/edit-contract-employee
  (s/keys :req [:user/given-name :user/family-name
                :user/person-id :user/email
                :company-contract-employee/role]))

