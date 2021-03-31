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

(defn- fetch-cost-item [adb id]
  (asset-type-library/db->form
   (asset-type-library/rotl-map
    (asset-db/asset-type-library adb))
   (d/pull adb '[*] id)))

(defquery :asset/project-cost-items
  {:doc "Query project cost items"
   :context {:keys [db user] adb :asset-db}
   :args {project-id :thk.project/id
          cost-item :cost-item}
   :project-id [:thk.project/id project-id]
   ;; fixme: cost items authz
   :authorization {:project/read-info {}}}
  (merge
   {:asset-type-library (asset-db/asset-type-library adb)
    :cost-items (asset-db/project-cost-items adb project-id)
    :project (project-db/project-by-id db [:thk.project/id project-id])}
   (when cost-item
     {:form (fetch-cost-item adb cost-item)})))

(defquery :asset/cost-item
  {:doc "Fetch a single cost item by id"
   :context {:keys [db user] adb :asset-db}
   :args {id :db/id}
   :project-id [:thk.project/id (asset-db/cost-item-project adb id)]
   :authorization {:project/read-info {}}}
  (fetch-cost-item adb id))
