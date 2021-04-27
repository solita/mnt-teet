(ns teet.contract.contract-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.contract.contract-db :as contract-db]
            [teet.notification.notification-queries :as notification-queries]))

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
  {:doc "Return a list of contracts matching given search params"
   :context {db :db user :user}
   :args {contract-ids :contract-ids}
   :project-id nil
   :authorization {}}
  (let [[contract-id contract-part-id] contract-ids]
    (-> (contract-db/get-contract
          db
          {:thk.contract/procurement-id contract-id
           :thk.contract/procurement-part-id contract-part-id})
        (update
          :thk.contract/targets
          #(map (partial target-with-navigation-info db) %)))))
