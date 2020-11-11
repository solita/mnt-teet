(ns teet.migration.thk-integration-id
  (:require [datomic.client.api :as d]
            [clojure.set :as set]
            [teet.thk.thk-mapping :as thk-mapping]))

(defn- entities-with-attr [db attr]
  (into #{}
        (map first)
        (d/q [:find '?e :where ['?e attr '_]] db)))

(defn migrate
  "Migrate existing :db/id values to UUID :integration/id"
  [conn]
  (let [db (d/db conn)
        lifecycles (entities-with-attr db :thk.lifecycle/id)
        activities (entities-with-attr db :activity/name)
        tasks (entities-with-attr db :task/send-to-thk?)
        entities (set/union lifecycles activities tasks)]
    (d/transact
     conn
     {:tx-data (vec
                (for [e entities]
                  {:db/id e
                   :integration/id (thk-mapping/number->uuid e)}))})))

(defn check-uuids [db]
  (every?
   #(let [{db-id :db/id
           integration-id :integration/id}
          (d/pull db [:db/id :integration/id] %)]
      (println "Check :db/id = " db-id ", :integration/id = " integration-id)
      (= db-id (thk-mapping/uuid->number integration-id)))
   (entities-with-attr db :thk.project/id)))

(defn migrate-projects
  "Migrate :integration/id for THK projects as well"
  [conn]
  (let [db (d/db conn)
        projects (entities-with-attr db :thk.project/id)]
    (d/transact
     conn
     {:tx-data (vec
                (for [p projects]
                  {:db/id p
                   :integration/id (thk-mapping/number->uuid p)}))})))
