(ns teet.migration.retract-old-imported-assets
  (:require [datomic.client.api :as d]
            [jeesql.core :refer [defqueries]]
            [teet.environment :as environment]
            [teet.log :as log]
            [teet.asset.asset-db :as asset-db]))

(declare delete-geometries!)
(defqueries "teet/migration/retract_old_imported_assets.sql")

(defn retract-old-assets [conn]
  ;; check if the old :asset/road-registry-oid exists
  (let [db (d/db conn)
        progress! (log/progress-fn 1 "batches of 500 old imported assets removed")]
    (when (:db/id (d/pull db '[:db/id] :asset/road-registry-oid))
      (log/info "Old RR OID attribute exists, retracting assets in the background")
      (future
        (with-open [sql-conn (environment/get-pg-connection)]
          (doseq [assets (partition-all
                          500
                          (d/qseq '[:find ?e ?oid
                                    :where
                                    [?e :asset/road-registry-oid _]
                                    [?e :asset/oid ?oid]]
                                  db))
                  :let [oids (map second assets)
                        all-oids (into oids
                                       (mapcat #(asset-db/asset-component-oids db %))
                                       oids)]]
            (delete-geometries! {:connection sql-conn} {:oids all-oids})
            (d/transact conn
                        {:tx-data (for [[id _] assets]
                                    [:db/retractEntity id])})
            (progress!)))))))
