(ns teet.contract.contract-queries
  (:require [teet.db-api.core :refer [defquery]]
            [datomic.client.api :as d]
            [teet.util.collection :as cu]
            [clojure.string :as str]
            [teet.contract.contract-db :as contract-db]))

(defquery :contract/contract-page
  {:doc "Return a list of contracts matching given search params"
   :context {db :db user :user}
   :args {contract-ids :contract-ids}
   :project-id nil
   :authorization {}}
  (let [[contract-id contract-part-id] contract-ids]
    (contract-db/get-contract
      db
      {:thk.contract/procurement-id contract-id
       :thk.contract/procurement-part-id contract-part-id})))
