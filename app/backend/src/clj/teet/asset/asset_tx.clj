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

(defn import-assets
  "Import assets of the given type from road registry.
  Creates new OIDs for asset/components if they don't exist yet."
  [db owner-code fclass assets]

  (let [{:fclass/keys [oid-prefix oid-sequence-number]}
        (d/pull db '[:fclass/oid-prefix :fclass/oid-sequence-number] fclass)

        asset-seq-num (atom (or oid-sequence-number 0))
        next-asset-oid! #(asset-model/asset-oid owner-code oid-prefix
                                                (swap! asset-seq-num inc))]
    (conj
     (mapv
      (fn [{rr-oid :asset/road-registry-oid :as asset}]
        (let [existing-oid (:asset/oid (d/pull db '[:asset/oid]
                                               [:asset/road-registry-oid rr-oid]))
              oid (or existing-oid
                      (next-asset-oid!))

              component-seq-num (atom (asset-db/max-component-oid-number
                                       db oid))
              next-component-id! #(asset-model/component-oid
                                   oid
                                   (swap! component-seq-num inc))]
          (-> asset
              (assoc :asset/oid oid)
              (update :asset/components
                      ;; FIXME: currently only 1st level of components
                      ;; supported in import.
                      (fn [components]
                        (mapv #(assoc % :asset/oid (next-component-id!))
                              components))))))
      assets)

     ;; Update asset OID counter
     {:db/ident fclass
      :fclass/oid-sequence-number @asset-seq-num})))

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
