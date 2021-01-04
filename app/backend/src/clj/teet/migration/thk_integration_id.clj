(ns teet.migration.thk-integration-id
  (:require [datomic.client.api :as d]
            [clojure.set :as set]
            [taoensso.timbre :as log]
            [teet.integration.integration-id :as integration-id]))

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
                   :integration/id (integration-id/number->uuid e)}))})))

(defn entities-including-attr-and-missing-iid [db attr]
  (into #{}
        (map first)
        (d/q [:find '?e
              :where ['?e attr '_]
              '[(missing? $ ?e :integration/id)]
              ] db)))


(defn migrate-2
  "Migrate existing :db/id values to UUID :integration/id, for those lc/activity/task entities that are still missing integration id"
  [conn]
  (let [db (d/db conn)
        lifecycles (entities-including-attr-and-missing-iid db :thk.lifecycle/id)
        activities (entities-including-attr-and-missing-iid db :activity/name)
        tasks (entities-including-attr-and-missing-iid db :task/send-to-thk?)
        entities (set/union lifecycles activities tasks)]    
    (d/transact
     conn
     {:tx-data (vec
                (for [e entities]
                  (let [new-iid (integration-id/unused-random-small-uuid db)]
                    (log/info "assigning entity" e "integration id" new-iid)
                    {:db/id e
                     :integration/id new-iid})))})))

(defn check-uuids [db]
  (every?
   #(let [{db-id :db/id
           integration-id :integration/id}
          (d/pull db [:db/id :integration/id] %)]
      (println "Check :db/id = " db-id ", :integration/id = " integration-id)
      (= db-id (integration-id/uuid->number integration-id)))
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
                   :integration/id (integration-id/number->uuid p)}))})))
