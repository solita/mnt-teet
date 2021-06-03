(ns teet.company.company-queries
  (:require
    [teet.db-api.core :refer [defquery]]
    [teet.company.company-db :as company-db]))

(defquery :company/search
  {:doc "Return a list of matching companies"
   :context {db :db user :user}
   :args {contract-eid :contract-eid
          search-term :search-term :as payload}
   :project-id nil
   :authorization {}}
  (company-db/company-search db search-term contract-eid))
