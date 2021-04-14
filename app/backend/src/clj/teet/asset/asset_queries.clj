(ns teet.asset.asset-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.project.project-db :as project-db]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-model :as asset-model]))

(defquery :asset/type-library
  {:doc "Query the asset types"
   :context _
   :unauthenticated? true
   :args _}
  (asset-db/asset-type-library (environment/asset-db)))

(defn- fetch-cost-item [adb oid]
  (asset-type-library/db->form
   (asset-type-library/rotl-map
    (asset-db/asset-type-library adb))
   (d/pull adb '[*] [:asset/oid oid])))

(defquery :asset/project-cost-items
  {:doc "Query project cost items"
   :context {:keys [db user] adb :asset-db}
   :args {project-id :thk.project/id
          cost-item :cost-item
          cost-totals :cost-totals}
   :project-id [:thk.project/id project-id]
   ;; fixme: cost items authz
   :authorization {:project/read-info {}}}
  (merge
   {:asset-type-library (asset-db/asset-type-library adb)
    :cost-items (asset-db/project-cost-items adb project-id)
    :project (project-db/project-by-id db [:thk.project/id project-id])}
   (when cost-totals
     (let [cost-groups (asset-db/project-cost-groups-totals adb project-id)]
       {:cost-totals
        {:cost-groups cost-groups
         :total-cost (str (reduce + (keep :total-cost cost-groups)))}}))
   (when cost-item
     {:cost-item (fetch-cost-item
                  adb
                  ;; Always pull the full asset even when focusing on a
                  ;; specific subcomponent.
                  ;; PENDING: what if there are thousands?
                  (if (asset-model/component-oid? cost-item)
                    (asset-model/component-asset-oid cost-item)
                    cost-item))})))

(defquery :asset/project-summary
  {:doc "Summary of project cost items grouped by cost groupings."
   :context {:keys [db user] adb :asset-db}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :authorization {:project/read-info {}}}
  :fixme)
