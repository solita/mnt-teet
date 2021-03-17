(ns teet.asset.asset-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.util.datomic :as du]
            [clojure.walk :as walk]
            [teet.project.project-db :as project-db]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]))

(defquery :asset/type-library
  {:doc "Query the asset types"
   :context _
   :unauthenticated? true
   :args _}
  (asset-db/asset-type-library (environment/asset-db)))

(defquery :asset/project-cost-items
  {:doc "Query project cost items"
   :context {:keys [db user] adb :asset-db}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   ;; fixme: cost items authz
   :authorization {:project/read-info {}}}
  {:asset-type-library (asset-db/asset-type-library adb)
   :cost-items (asset-db/project-cost-items adb project-id)
   :project (project-db/project-by-id db [:thk.project/id project-id])})

(defquery :asset/cost-item
  {:doc "Fetch a single cost item by id"
   :context {:keys [db user] adb :asset-db}
   :args {id :db/id}
   :project-id [:thk.project/id (asset-db/cost-item-project adb id)]
   :authorization {:project/read-info {}}}
  (asset-type-library/db->form (d/pull adb '[*] id)))
