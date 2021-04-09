(ns teet.asset.asset-tx
  "Asset transaction functions"
  (:require [teet.asset.asset-db :as asset-db]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

(defn save-asset
  "Create or update asset. Creates new OID based on the fclass."
  [db {:asset/keys [fclass oid] :as asset}]
  (if oid
    ;; update existing
    (du/modify-entity-retract-nils db asset)

    ;; Create new OID and asset
    (let [[update-counter-tx oid] (asset-db/next-oid db fclass)]
      [update-counter-tx
       (cu/without-nils
        (assoc asset :asset/oid oid))])))
