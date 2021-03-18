(ns teet.asset.asset-commands
  "Commands to store asset information"
  (:require [teet.db-api.core :refer [defcommand] :as db-api]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [clojure.walk :as walk]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.util.collection :as cu]))




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
                 (for [{deleted-id :db/id} deleted]
                   [:db/retractEntity deleted-id]))]
    (def *asset asset)
    (def *tx tx)
    ;; remove :deleted? entries from tree add retractions for those
    (:tempids
     (d/transact aconn
                 {:tx-data tx}))))
