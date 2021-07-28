(ns teet.migration.user-login
  (:require [datomic.client.api :as d]))

(defn set-user-last-login
  "Updates the last-login attribute for every user to 1.1.1970 if it's not set and the user has required information"
  [conn]
  (let [db (d/db conn)
        users (d/q '[:find ?u
               :in $
               :where
               [?u :user/person-id _]
               [?u :user/given-name _]
               [?u :user/family-name _]
               [(missing? $ ?u :user/last-login)]]
             db)
        transaction (->> users
                         (mapv first)
                         (mapv (fn [u]
                                 {:db/id u
                                  :user/last-login #inst "1970-01-01T00:00:00"})))]
    (d/transact conn {:tx-data transaction})))