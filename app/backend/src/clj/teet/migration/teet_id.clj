(ns teet.migration.teet-id
  (:require [datomic.client.api :as d]))

(defn cooperation-teet-ids [conn]
  (d/transact
   conn
   {:tx-data (for [[id] (d/q '[:find ?e
                               :where
                               (not [?e :teet/id _])
                               (or
                                [?e :cooperation.3rd-party/name _]
                                [?e :cooperation.application/type _])]
                             (d/db conn))]
               {:db/id id
                :teet/id (java.util.UUID/randomUUID)})}))
