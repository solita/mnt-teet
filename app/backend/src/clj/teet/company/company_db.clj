(ns teet.company.company-db
  (:require [datomic.client.api :as d]))

(defn company-search
  "Finds from both company business registry code or company name that are not in the given contract"
  [db search-term contract-id]
  (->> (d/q '[:find (pull ?company [:db/id
                                   :company/name
                                   :company/emails
                                   :company/phone-numbers
                                   :company/business-registry-code
                                   :company/country])
             :where
             (or-join [?company ?search-term]
                      (and [?company :company/name ?company-name]
                           [(teet.util.string/contains-words? ?company-name ?search-term)])
                      (and [?company :company/business-registry-code ?company-code]
                           [(teet.util.string/contains-words? ?company-code ?search-term)]))
             ;; dont find companies already added to the given contract
             (not-join [?company ?contract]
                       [?company-contract :company-contract/company ?company]
                       [?company-contract :company-contract/contract ?contract])
             :in $ ?search-term ?contract]
           db
           search-term
           contract-id)
      (mapv first)))

(defn business-registry-code-unique?
  "Checks if the given business registry code doesn't exist in the db"
  [db business-registry-code]
  (empty? (d/q '[:find ?company
                 :where
                 [?company :company/business-registry-code ?business-registry-code]
                 :in $ ?business-registry-code]
               db
               business-registry-code)))

(defn company-in-contract?
  [db contract-eid company-eid]
  (-> (d/q '[:find ?company-contract
             :where
             [?company-contract :company-contract/company ?company]
             [?company-contract :company-contract/contract ?contract]
             :in $ ?contract ?company]
           db contract-eid company-eid)
      not-empty
      boolean))

(defn is-company?
  "Check if the given company-id is actually a company"
  [db company-id]
  (-> (d/q '[:find ?c
             :where
             [?c :company/business-registry-code _]
             :in $ ?c]
           db
           company-id)
      not-empty
      boolean))
