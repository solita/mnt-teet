(ns teet.contract.contract-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.contract.contract-db :as contract-db]
            [teet.notification.notification-queries :as notification-queries]
            [teet.util.datomic :as du]
            [teet.util.collection :as cu]))

(defn target-with-navigation-info
  [db target]
  (cond
    (:activity/name target)
    (assoc target :navigation-info (notification-queries/activity-navigation-info db (:db/id target)))
    (:task/name target)
    (assoc target :navigation-info (notification-queries/task-navigation-info db (:db/id target)))
    :else
    target))

(defquery :contract/contract-page
  {:doc "Return the single contracts information"
   :context {db :db user :user}
   :args {contract-ids :contract-ids}
   :project-id nil
   :authorization {}}
  (let [[contract-id contract-part-id] contract-ids]
    (-> (contract-db/get-contract
          db
          [:thk.contract/procurement-id+procurement-part-id [contract-id contract-part-id]])
        (update
          :thk.contract/targets
          #(map (partial target-with-navigation-info db) %))
        (cu/update-in-if-exists [:thk.contract/cost] str)
        (cu/update-in-if-exists [:thk.contract/warranty-period] str)
        du/idents->keywords)))
