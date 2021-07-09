(ns teet.company.company-queries
  (:require
    [teet.db-api.core :refer [defquery]]
    [teet.company.company-db :as company-db]
    [teet.integration.x-road.business-registry :as business-registry]
    [clojure.walk :as walk])
  (:import (java.time LocalDate)
           (java.time.format DateTimeFormatter)))

(defquery :company/search
  {:doc "Return a list of matching companies"
   :context {db :db user :user}
   :args {contract-eid :contract-eid
          search-term :search-term :as payload}
   :allowed-for-all-users? true}
  (company-db/company-search db search-term contract-eid))

(defquery :company/business-registry-search
  {:doc "Fetch information about a given business registry code"
   :context {:keys [db user]}
   :args {:keys [business-id]}
   :allowed-for-all-users? true
   :config {xroad-instance [:xroad :instance-id]
            xroad-url [:xroad :query-url]
            xroad-subsystem [:xroad :kr-subsystem-id]}}
  (let [response
        (business-registry/perform-business-information-request
          xroad-url {:business-id business-id
                     :instance-id xroad-instance
                     :requesting-eid (:user/person-id user)})]
    (walk/prewalk
      (fn [x]
        (if (instance? LocalDate x)
          (.format x DateTimeFormatter/ISO_LOCAL_DATE)
          x))
      response)))
