(ns teet.asset.asset-tx
  "Asset transaction functions"
  (:require [teet.asset.asset-db :as asset-db]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.asset.asset-model :as asset-model]
            [datomic.client.api :as d]
            [clojure.walk :as walk]))

(def ^:const bigdec-scale
  "All asset db decimal values use the same 6 decimal place scale."
  6)

(defn set-bigdec-scale
  "Walk tx-data and set all bigdec scale."
  [tx-data]
  (walk/prewalk
   (fn [x]
     (if (decimal? x)
       (.setScale x bigdec-scale)
       x))
   tx-data))

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
  [db parent-oid {id :db/id
                  type :material/type
                  :as material}]
    (when-not (asset-db/leaf-component? db [:asset/oid parent-oid])
    (throw (ex-info "Not a leaf component OID"
                    {:parent-oid parent-oid})))
  (let [existing-material-ids
        (into #{}
              (map first)
              (d/q '[:find ?m
                     :where
                     [?c :component/materials ?m]
                     [?m :material/type ?t]
                     :in $ ?c ?t]
                   db [:asset/oid parent-oid] type))]

    (when (seq (disj existing-material-ids id))
      (throw (ex-info "Material of the same type already exists for component"
                      {:parent-oid parent-oid
                       :type type}))))
  ;; TODO: spec
  (when (not (:material/type material))
    (throw (ex-info "No material type"
                    {:parent-oid parent-oid})))
  (if (number? id)
    (du/modify-entity-retract-nils db material)
    [[:db/add [:asset/oid parent-oid]
      :component/materials
      (:db/id material)]
     (cu/without-nils
      (assoc material
             :asset/oid
             (asset-db/next-material-oid db parent-oid)))]))

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
