(ns teet.asset.asset-tx
  "Asset transaction functions"
  (:require [teet.asset.asset-db :as asset-db]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.asset.asset-model :as asset-model]))

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
