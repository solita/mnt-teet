(ns teet.asset.asset-commands
  "Commands to store asset information"
  (:require [teet.db-api.core :refer [defcommand] :as db-api]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [clojure.walk :as walk]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))




(defcommand :asset/save-cost-item
  {:doc "Create/update an asset cost item"
   :context {:keys [user db]
             adb :asset-db
             aconn :asset-conn}
   :payload {project-id :project-id asset :asset}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/edit-cost-items {}}}
  (let [asset (merge {:asset/project project-id}
                     (asset-type-library/form->db
                      (asset-type-library/rotl-map (asset-db/asset-type-library adb))
                      asset))
        deleted (cu/collect :deleted? asset)
        asset (cu/without :deleted? asset)
        tx (into [asset]
                 (for [{deleted-id :db/id} deleted
                       :when deleted-id]
                   [:db/retractEntity deleted-id]))]
    (:tempids
     (d/transact aconn
                 {:tx-data tx}))))

(defcommand :asset/delete-component
  {:doc "Delete a component in an existing asset."
   :context {:keys [user db]
             adb :asset-db
             aconn :asset-conn}
   :payload {project-id :project-id component-id :db/id}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/edit-cost-items {}}
   :pre [(= project-id (asset-db/component-project adb component-id))]
   :transact
   ^{:db :asset}
   [[:db/retractEntity component-id]]})
