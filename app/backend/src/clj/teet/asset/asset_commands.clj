(ns teet.asset.asset-commands
  "Commands to store asset information"
  (:require [teet.db-api.core :refer [defcommand tx] :as db-api]
            [datomic.client.api :as d]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.util.datomic :as du]
            [teet.util.euro :as euro]
            [teet.asset.asset-model :as asset-model]))


(defn- boq-unlocked? [db thk-project-id]
  (not (asset-model/locked?
        (asset-db/project-boq-version db thk-project-id))))

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
             (= project-id (:asset/project (du/entity adb (:db/id asset)))))
         ^{:error :boq-is-locked}
         (boq-unlocked? adb project-id)]}
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
   :authorization {:cost-items/delete-cost-items {}}
   :pre [(= project-id (asset-db/component-project adb component-id))
         ^{:error :boq-is-locked}
         (boq-unlocked? adb project-id)]
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
             (= project-id (asset-db/component-project adb (:db/id component))))
         ^{:error :boq-is-locked}
         (boq-unlocked? adb project-id)]}
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

(defn- valid-cost-group-price?
  "We want the price to be
   - non-negative
   - max eurocent precision"
  [price]
  (try (not (neg? (euro/parse price)))
       (catch Exception _e
         false)))

(defcommand :asset/save-cost-group-price
  {:doc "Save cost group price"
   :context {:keys [user db] adb :asset-db}
   :payload {:keys [project-id cost-group price]}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/edit-cost-items {}}
   :pre [^{:error :cost-group-price-does-not-belong-to-project}
         (or (nil? (:db/id cost-group))
             (= project-id
                (:cost-group/project (du/entity adb (:db/id cost-group)))))
         ^{:error :boq-is-locked}
         (boq-unlocked? adb project-id)

         ^{:error :invalid-price}
         (valid-cost-group-price? price)]}
  (tx
   (if-let [id (:db/id cost-group)]
     ;; Compare and swap the price if there is an existing one
     ^{:db :asset}
     [[:db/cas id :cost-group/price
       (euro/parse (:cost-group/price cost-group))
       (euro/parse price)]]

     ;; Create new cost group price
     ^{:db :asset}
     [(merge
       (asset-type-library/form->db (asset-type-library/rotl-map
                                     (asset-db/asset-type-library adb))
                                    cost-group)
       {:db/id "new-cost-group-price"
        :cost-group/price (euro/parse price)
        :cost-group/project project-id})]))
  :ok)

(defcommand :asset/lock-version
  {:doc "Lock a version of BOQ"
   :context {:keys [user db] adb :asset-db}
   :payload lock-version
   :project-id [:thk.project/id (:boq-version/project lock-version)]
   :authorization {:cost-items/locking-unlocking-and-versioning {}}
   :pre [^{:error :boq-is-locked}
         (boq-unlocked? adb (:boq-version/project lock-version))]
   :transact
   ^{:db :asset}
   [(list 'teet.asset.asset-tx/lock lock-version)]})

(defcommand :asset/unlock-for-edits
  {:doc "Unlock version for edits"
   :context {:keys [user db] adb :asset-db}
   :payload {project-id :boq-version/project}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/locking-unlocking-and-versioning {}}
   :pre [^{:error :boq-is-unlocked}
         (not (boq-unlocked? adb project-id))]
   :transact
   ^{:db :asset}
   [{:db/id "unlock"
     :boq-version/created-at (java.util.Date.)
     :boq-version/project project-id
     :boq-version/locked? false}]})
