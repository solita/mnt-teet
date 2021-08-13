(ns teet.asset.asset-commands
  "Commands to store asset information"
  (:require [teet.db-api.core :refer [defcommand tx] :as db-api]
            [datomic.client.api :as d]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.util.datomic :as du]
            [teet.util.euro :as euro]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-geometry :as asset-geometry]))


(defn- boq-unlocked? [db thk-project-id]
  (not (asset-model/locked?
        (asset-db/project-boq-version db thk-project-id))))

(defcommand :asset/save-cost-item
  {:doc "Create/update an asset cost item"
   :context {adb :asset-db
             sql-conn :sql-conn}
   :payload {project-id :project-id asset :asset}
   :config {owner-code [:asset :default-owner-code]}
   :project-id [:thk.project/id project-id]
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
                        (dissoc asset :asset/components))))])
        asset (d/pull db-after [:asset/oid]
                      (if (string? id)
                        (tempids id)
                        id))]
    (asset-geometry/update-asset! db-after sql-conn (:asset/oid asset))
    asset))

(defcommand  :asset/delete-component
  {:doc "Delete a component in an existing asset."
   :context {adb :asset-db}
   :payload {project-id :project-id component-id :db/id}
   :project-id [:thk.project/id project-id]
   :pre [(= project-id (asset-db/component-project adb component-id))
         ^{:error :boq-is-locked}
         (boq-unlocked? adb project-id)]
   :transact
   ^{:db :asset}
   [[:db/retractEntity component-id]]})

(defcommand :asset/save-component
  {:doc "Save component for an asset or component."
   :context {adb :asset-db
             sql-conn :sql-conn}
   :payload {:keys [project-id parent-id component]}
   :project-id [:thk.project/id project-id]
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
                 (dissoc component :component/components)))])
        component (d/pull db-after [:asset/oid]
                          (if (string? id)
                            (tempids id)
                            id))]
    (asset-geometry/update-asset! db-after sql-conn
                                  (asset-model/component-asset-oid (:asset/oid component)))
    component))

(defcommand :asset/save-material
  {:doc "Save material for a leaf component."
   :context {adb :asset-db}
   :payload {:keys [project-id parent-id material]}
   :project-id [:thk.project/id project-id]
   :pre [(or (string? (:db/id material))
             (= project-id (asset-db/material-project adb (:db/id material))))
         ^{:error :boq-is-locked}
         (boq-unlocked? adb project-id)]}
  (let [id (:db/id material)
        {:keys [db-after tempids]}
        (tx
         ^{:db :asset}
         [(list 'teet.asset.asset-tx/save-material
                parent-id
                (asset-type-library/form->db
                 (asset-type-library/rotl-map (asset-db/asset-type-library adb))
                 material))])]
    (d/pull db-after [:asset/oid]
            (if (string? id)
              (tempids id)
              id))))

(defcommand :asset/delete-material
  {:doc "Delete a material in an existing component."
   :context {adb :asset-db}
   :payload {project-id :project-id material-id :db/id}
   :project-id [:thk.project/id project-id]
   :pre [(= project-id (asset-db/material-project adb material-id))
         ^{:error :boq-is-locked}
         (boq-unlocked? adb project-id)]
   :transact
   ^{:db :asset}
   [[:db/retractEntity material-id]]})

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
   :context {adb :asset-db}
   :payload {:keys [project-id cost-group price]}
   :project-id [:thk.project/id project-id]
   :pre [^{:error :cost-group-price-does-not-belong-to-project}
         (or (nil? (:db/id cost-group))
             (= project-id
                (:cost-group/project (du/entity adb (:db/id cost-group)))))
         ^{:error :boq-is-locked}
         (boq-unlocked? adb project-id)

         ^{:error :invalid-price}
         (or (nil? price)
             (valid-cost-group-price? price))]}
  ;; Are we inserting or updating?
  (if-let [id (:db/id cost-group)]
    ;; Update: If we have a new price...
    (let [current-cost-group-price (some-> (:cost-group/price cost-group) euro/parse)]
      (if price
        ;; Compare and swap the price if there is an existing one
        (tx ^{:db :asset}
            [[:db/cas id :cost-group/price
              current-cost-group-price
              (euro/parse price)]])
        ;; Else retract the cost group price
        (tx ^{:db :asset}
            [[:db/retract id :cost-group/price]])))
    ;; Insert: Create new cost group price (if no price, no tx necessary)
    (when price
      (tx ^{:db :asset}
          [(merge
            (asset-type-library/form->db (asset-type-library/rotl-map
                                          (asset-db/asset-type-library adb))
                                         cost-group)
            {:db/id "new-cost-group-price"
             :cost-group/price (euro/parse price)
             :cost-group/project project-id})])))
  :ok)

(defcommand :asset/lock-version
  {:doc "Lock a version of BOQ"
   :context {adb :asset-db}
   :payload lock-version
   :project-id [:thk.project/id (:boq-version/project lock-version)]
   :authorization {:cost-items/locking-unlocking-and-versioning {}}
   :contract-authorization {:action :cost-items/locking-unlocking-and-versioning}
   :pre [^{:error :boq-is-locked}
         (boq-unlocked? adb (:boq-version/project lock-version))]
   :transact
   ^{:db :asset}
   [(list 'teet.asset.asset-tx/lock lock-version)]})

(defcommand :asset/unlock-for-edits
  {:doc "Unlock version for edits"
   :context {adb :asset-db}
   :payload {project-id :boq-version/project}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/locking-unlocking-and-versioning {}}
   :contract-authorization {:action :cost-items/locking-unlocking-and-versioning}
   :pre [^{:error :boq-is-unlocked}
         (not (boq-unlocked? adb project-id))]
   :transact
   ^{:db :asset}
   [{:db/id "unlock"
     :boq-version/created-at (java.util.Date.)
     :boq-version/project project-id
     :boq-version/locked? false}]})
