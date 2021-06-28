(ns teet.asset.asset-geometry
  "Update asset/component geometries to PostgreSQL."
  (:require [jeesql.core :refer [defqueries]]
            [teet.integration.postgresql :refer [with-transaction]]
            [datomic.client.api :as d]
            [teet.asset.asset-db :as asset-db]
            [clojure.string :as str]
            [teet.log :as log]
            [teet.environment :as environment]))

(declare store-geometry!)
(defqueries "teet/asset/asset_geometry.sql")

(defn- points->wkt [& points]
  (str "LINESTRING("
       (str/join ","
                 (map #(str/join " " %) points))
       ")"))

(defn update-asset!
  "Update a single asset's geometries to PostgreSQL table.
  This includes the main asset and any components."
  [asset-db sql-conn oid]
  (with-transaction sql-conn
    (doseq [[oid start end]
            (d/q '[:find ?oid ?start ?end
                   :where
                   [?a :asset/oid ?oid]
                   [?a :location/start-point ?start]
                   [?a :location/end-point ?end]
                   :in $ [?oid ...]]
                 asset-db
                 (into [oid] (asset-db/asset-component-oids asset-db oid)))
            :let [wkt (points->wkt start end)]]
      (store-geometry! sql-conn {:oid oid :geometry wkt}))))

(defn update-all-assets!
  "Update all asset geometries. This will take a long time."
  [asset-db sql-conn]
  (let [prg (log/progress-fn "Asset geometries upserted to PostgreSQL")]
    (doseq [oid (filter
                 (fn [oid]
                   ;; only asset OIDs
                   (= 14 (count oid)))
                 (map :v (d/datoms asset-db
                                   {:index :avet
                                    :components [:asset/oid]
                                    :limit -1})))]
      (update-asset! asset-db sql-conn oid)
      (prg))))

(defn update-ion
  [_event]
  (future
    (log/info "Updating all assets to SQL")
    (environment/call-with-pg-connection
     #(update-all-assets! (environment/asset-db) %))))
