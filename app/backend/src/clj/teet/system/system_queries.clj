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

;; Return db stats datom counts for teet and asset (if enabled) databases
(defquery :teet.system/db
  {:doc "Check database status"
   :context {db :db}
   :spec empty?
   :args _
   :unauthenticated? true}
  (merge
   {:teet (:datoms (d/db-stats db))}
   (when (environment/feature-enabled? :asset-db)
     {:asset (:datoms (d/db-stats (environment/asset-db)))})))
