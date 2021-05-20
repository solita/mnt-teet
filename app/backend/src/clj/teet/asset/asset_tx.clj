(ns teet.asset.asset-tx
  "Asset transaction functions"
  (:require [teet.asset.asset-db :as asset-db]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.asset.asset-model :as asset-model]
            [datomic.client.api :as d]
            [teet.meta.meta-model :as meta-model]
            [clojure.walk :as walk]))

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

(defn- collect-oids [form]
  (cu/collect #(and (map-entry? %)
                    (= :asset/oid (first %)))
              form))

(defn import-asset
  "Import full asset with components. Creates new OIDs for asset and all
  the components if they are not present."
  [db owner-code {existing-oid :asset/oid :as asset}]
  {:pre [(or
          ;; No asset OID at toplevel, check that there are no OIDs
          ;; in child components either
          (and (nil? existing-oid)
               (empty? (collect-oids (:asset/components asset))))

          ;; Asset provided at toplevel, check that all component OIDs
          ;; are sub-OIDs of the asset
          (and (asset-model/asset-oid? existing-oid)
               (every? (fn [[_ component-oid]]
                         (= existing-oid
                            (asset-model/component-asset-oid component-oid)))
                       (collect-oids (:asset/components asset)))))]}
  (let [oid (or existing-oid
                (asset-db/next-oid db owner-code (:asset/fclass asset)))]
    [(-> asset
         (assoc :asset/oid oid)
         (update :asset/components
                 (fn [components]
                   (walk/prewalk
                    (fn [x]
                      (if (and (map? x)
                               (contains? x :component/ctype)
                               (not (contains? x :asset/oid)))
                        (assoc x :asset/oid (asset-db/next-component-oid db oid))
                        x))
                    components))))]))
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
