(ns teet.system.system-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.util.build-info :as build-info]
            [teet.environment :as environment]
            [datomic.client.api :as d]))

(defn db-state-response [_db-result]
  (with-meta
    {:status 200
     :body (build-info/git-commit)}
    {:format :raw}))

;; The actual query here is irrelevant. This provides an endpoint for
;; checking whether the db is alive.
(defquery :teet.system/db
  {:doc "Check database status"
   :context {db :db}
   :spec empty?
   :args _
   :unauthenticated? true}
  (db-state-response
   (merge
    {:teet (d/q '[:find ?e
                  :where
                  [?e :thk.project/project-name "non-existent"]]
                db)}
    (when (environment/feature-enabled? :asset-db)
      {:asset (d/pull (environment/asset-db) [:db/doc] :tx/schema-hash)}))))
