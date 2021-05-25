(ns teet.asset.asset-tx
  "Asset transaction functions"
  (:require [teet.asset.asset-db :as asset-db]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.asset.asset-model :as asset-model]
            [datomic.client.api :as d]
            [teet.meta.meta-model :as meta-model]))

(defn save-asset
  "Create or update asset. Creates new OID based on the fclass."
  [db owner-code {id :db/id :asset/keys [fclass] :as asset}]
  (if (number? id)
    ;; update existing
    (du/modify-entity-retract-nils db asset)

    ;; Create new OID and asset
    (let [[update-counter-tx oid] (asset-db/next-oid db owner-code fclass)]
      [update-counter-tx
       (cu/without-nils
        (assoc asset :asset/oid oid))])))

(defn save-component
  "Create or update component.
  Creates new OID for new components based on parent asset."
  [db parent-oid {id :db/id :as component}]
  (if (number? id)
    (du/modify-entity-retract-nils db component)
    (let [asset-oid (if (asset-model/asset-oid? parent-oid)
                      parent-oid
                      (asset-model/component-asset-oid parent-oid))
          component-oid (asset-db/next-component-oid db asset-oid)]
      [[:db/add [:asset/oid parent-oid]
        (cond
          (asset-model/asset-oid? parent-oid) :asset/components
          (asset-model/component-oid? parent-oid) :component/components
          :else (throw (ex-info "Unrecognized parent OID"
                                {:parent-oid parent-oid})))
        (:db/id component)]

       (cu/without-nils
        (assoc component :asset/oid component-oid))])))

(defn save-material
  "Create or update component.
  Creates new OID for new components based on parent asset."
  [db parent-oid {id :db/id :as material}]
  (when-not (asset-db/leaf-component? db [:asset/oid parent-oid])
    (throw (ex-info "Not a leaf component OID"
                    {:parent-oid parent-oid})))
  ;; TODO: spec
  (when (not (:material/type material))
    (throw (ex-info "No material type"
                    {:parent-oid parent-oid})))
  (if (number? id)
    (du/modify-entity-retract-nils db material)
    [[:db/add [:asset/oid parent-oid]
      :component/materials
      (:db/id material)]
     (cu/without-nils material)]))

(defn lock
  "Create new lock for project BOQ."
  [db {:boq-version/keys [project type] :as lock}]
  (let [number
        (inc (or (ffirst (d/q '[:find (max ?n)
                                :where
                                [?e :boq-version/project ?project]
                                [?e :boq-version/type ?type]
                                [?e :boq-version/number ?n]
                                :in $ ?project ?type]
                              db project type))
                 0))]
    [(merge
      {:db/id "lock"
       :boq-version/number number
       :boq-version/created-at (java.util.Date.)
       :boq-version/locked? true}
      (select-keys lock [:boq-version/type :boq-version/explanation
                         :boq-version/project]))]))
