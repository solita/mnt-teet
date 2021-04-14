(ns teet.asset.asset-commands
  "Commands to store asset information"
  (:require [teet.db-api.core :refer [defcommand tx] :as db-api]
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
   :config {owner-code [:asset :default-owner-code]}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/edit-cost-items {}}
   :pre [^{:error :asset-does-not-belong-to-project}
         (or (string? (:db/id asset))
             (= project-id (:asset/project (du/entity adb (:db/id asset)))))]}
  (let [id (:db/id asset)
        {:keys [db-after tempids]}
        (tx
         ^{:db :asset}
         [(list 'teet.asset.asset-tx/save-asset
                owner-code
                (merge {:asset/project project-id}
                       (asset-type-library/form->db
                        (asset-type-library/rotl-map (asset-db/asset-type-library adb))
                        (dissoc asset :asset/components))))])]
    (d/pull db-after [:asset/oid]
            (if (string? id)
              (tempids id)
              id))))

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
             (= project-id (asset-db/component-project adb (:db/id component))))]}
  (let [id (:db/id component)
        {:keys [db-after tempids]}
        (tx
         ^{:db :asset}
         [(list 'teet.asset.asset-tx/save-component
                parent-id
                (asset-type-library/form->db
                 (asset-type-library/rotl-map (asset-db/asset-type-library adb))
                 (dissoc component :component/components)))])]
    (d/pull db-after [:asset/oid]
            (if (string? id)
              (tempids id)
              id))))

(defcommand :asset/save-cost-group-price
  {:doc "Save cost group price"
   :context {:keys [user db] adb :asset-db}
   :payload {:keys [project-id cost-group price]}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/edit-cost-items {}}
   :pre [^{:error :cost-group-price-does-not-belong-to-project}
         (or (nil? (:db/id cost-group))
             (= project-id
                (:cost-group/project (du/entity adb (:db/id cost-group)))))]}
  (def *save {:project-id project-id
              :cost-group cost-group
              :price price})
  (tx
   (if-let [id (:db/id cost-group)]
     ;; Compare and swap the price if there is an existing one
     ^{:db :asset}
     [[:db/cas id :cost-group/price
       (asset-type-library/->bigdec (:cost-group/price cost-group))
       (asset-type-library/->bigdec price)]]

     ;; Create new cost group price
     ^{:db :asset}
     [(merge
       (asset-type-library/form->db (asset-type-library/rotl-map
                                     (asset-db/asset-type-library adb))
                                    cost-group)
       {:db/id "new-cost-group-price"
        :cost-group/price (asset-type-library/->bigdec price)
        :cost-group/project project-id})]))
  :ok)
