(ns teet.company.company-model
  (:require [clojure.string :as str]))

(defn company-business-registry-id-with-country-code
  [company]
  (let [company-country (-> company
                            :company/country
                            name
                            str/upper-case)]
    (str company-country (:company/business-registry-code company))))

(def company-keys
  [:company/business-registry-code
   :company/name
   :teet/id
   :db/id
   :company/phone-number
   :company/email
   :company/country])
