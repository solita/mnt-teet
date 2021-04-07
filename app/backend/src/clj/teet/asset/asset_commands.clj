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
   :authorization {:cost-items/edit-cost-items {}}
   :transact
   (with-meta
     (du/modify-entity-retract-nils
      adb
      (merge {:asset/project project-id}
             (asset-type-library/form->db
              (asset-type-library/rotl-map (asset-db/asset-type-library adb))
              (dissoc asset :asset/components))))
     {:db :asset})})

(defcommand :asset/delete-component
  {:doc "Delete a component in an existing asset."
   :context {:keys [user db]
             adb :asset-db}
   :payload {project-id :project-id component-id :db/id}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/edit-cost-items {}}
   :pre [(= project-id (asset-db/component-project adb component-id))]
   :transact
   ^{:db :asset}
   [[:db/retractEntity component-id]]})

(defcommand :asset/save-component
  {:doc "Save component for an asset or component."
   :context {:keys [user db]
             adb :asset-db}
   :payload {:keys [project-id parent-id component]}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/edit-cost-items {}}
   :pre [(or (string? (:db/id component))
             (= project-id (asset-db/component-project adb (:db/id component))))]
   :transact
   (with-meta
     (conj
      (du/modify-entity-retract-nils
       adb
       (asset-type-library/form->db
        (asset-type-library/rotl-map (asset-db/asset-type-library adb))
        (dissoc component :component/components)))
      ;; Link this to parent
      [:db/add parent-id
       (case (asset-db/item-type adb parent-id)
         :asset :asset/components
         :component :component/components) (:db/id component)])
     {:db :asset})})
