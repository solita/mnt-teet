(ns teet.asset.asset-db
  "Asset database Datomic queries.

  See [[teet.asset.asset-model]] namespace docstring for domain concept
  documentation."
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]
            [teet.util.datomic :as du]
            [teet.util.collection :as cu]
            [clojure.string :as str]
            [teet.asset.asset-model :as asset-model]))

(def ctype-pattern
  '[*
    {:ctype/_parent [*]}
    {:attribute/_parent
     [*
      {:enum/_attribute [*]}]}])

(def type-library-pattern
  `[~'*
    {:fclass/_fgroup
     [~'*
      {:attribute/_parent [~'* {:enum/_attribute [~'*]}]}
      {:ctype/_parent ~ctype-pattern}]}])

(defn- ctype? [x]
  (and (map? x)
       (du/enum= (:asset-schema/type x) :asset-schema.type/ctype)))

(defn- child-ctypes [db ctype]
  (update ctype :ctype/_parent
          #(->> %
                (mapv
                 (fn [child]
                   (if (ctype? child)
                     (d/pull db ctype-pattern (:db/id child))
                     child))))))

(defn- remove-empty-selection-attributes [x]
  (if (and (map? x) (:attribute/_parent x))
    (update x :attribute/_parent
            (fn [attrs]
              (into []
                    (remove #(and (= (get-in % [:db/valueType :db/ident]) :db.type/ref)
                                  (empty? (:enum/_attribute %))))
                    attrs)))
    x))

(defn- pull-child-ctypes [db x]
  (if (ctype? x)
    (child-ctypes db x)
    x))

(defn asset-type-library [db]
  (walk/postwalk
   (fn [x]
     (->> x
          remove-empty-selection-attributes
          (pull-child-ctypes db)))

   {:ctype/common (d/pull db ctype-pattern :ctype/common)
    :ctype/location (d/pull db ctype-pattern :ctype/location)
    :fgroups (mapv first
                   (d/q '[:find (pull ?fg p)
                          :where [?fg :asset-schema/type :asset-schema.type/fgroup]
                          :in $ p]
                        db type-library-pattern))}))


(defn project-cost-items
  "Pull all assets for the given project. Group by fgroup/fclass."
  [adb thk-project-id]
  (->> (d/q '[:find (pull ?a [:db/id :asset/oid :common/name :common/status
                              {:asset/fclass [:db/ident :asset-schema/label
                                              {:fclass/fgroup [:db/ident :asset-schema/label]}]}])
              :where
              [?a :asset/project ?p]
              [?a :asset/fclass _]

              :in $ ?p]
            adb thk-project-id)

       (map first)
       ;; Group by fgroup
       (group-by #(get-in % [:asset/fclass :fclass/fgroup]))

       ;; For each fgroup, further group assets by fclass
       (cu/map-vals (fn [cost-items-for-fgroup]
                      (group-by #(-> % :asset/fclass (dissoc :fclass/fgroup))
                                cost-items-for-fgroup)))))

(defn asset-with-components
  "Pull asset and all its components with all attributes."
  [db asset-oid]
  {:pre [(asset-model/asset-oid? asset-oid)]}
  (map first
       (d/q '[:find (pull ?e [*])
              :where
              [?e :asset/oid ?oid]
              [(>= ?oid ?start)]
              [(< ?oid ?end)]
              :in $ ?start ?end]
            db asset-oid (str asset-oid "."))))

(defn- asset-component-oids
  "Return all OIDs of components (at any level) contained in asset."
  [db asset-oid]
  {:pre [(asset-model/asset-oid? asset-oid)]}
  (mapv :v
        (d/index-range db {:attrid :asset/oid
                           :start (str asset-oid "-")
                           :end (str asset-oid ".")})))

(defn project-asset-oids
  "Return all OIDs of assets in the given THK project."
  [db thk-project-id]
  (mapv first
        (d/q '[:find ?oid
               :where
               [?asset :asset/project ?project]
               [?asset :asset/oid ?oid]
               :in $ ?project]
             db thk-project-id)))

(defn project-asset-and-component-oids
  "Return all OIDs of assets and their components for the given THK project."
  [db thk-project-id]
  (mapcat #(into [%] (asset-component-oids db %))
          (project-asset-oids db thk-project-id)))

(defn- cost-group-attrs-q
  "Return all item attributes marked as cost grouping."
  [db thk-project-id]
  (let [entity-attr-vals
        (d/q '[:find ?a ?type-ident ?attr-ident ?val ?value-type-ident
               :keys id type attr val val-type
               :where
               [?a :asset/oid ?oid]
               (or
                [?a :asset/fclass ?type]
                [?a :component/ctype ?type])
               [?a ?attr ?val]
               [?attr :attribute/cost-grouping? true]
               [?attr :db/ident ?attr-ident]
               [?type :db/ident ?type-ident]
               [?attr :db/valueType ?value-type]
               [?value-type :db/ident ?value-type-ident]
               :in $ [?oid ...]]
             db (project-asset-and-component-oids db thk-project-id))

        ;; Fetch all list item ref ids
        list-item-ids (into #{}
                         (keep (fn [{:keys [val-type val]}]
                                 (when (= val-type :db.type/ref)
                                   val)))
                         entity-attr-vals)

        ;; Fetch map from db id => map of id and ident
        list-item-vals (into {}
                             (comp
                              (map first)
                              (map (juxt :db/id identity)))
                             (d/q '[:find (pull ?val [:db/id :db/ident])
                                    :in $ [?val ...]]
                                  db list-item-ids))]
    (for [{val-type :val-type :as r} entity-attr-vals]
      (if (= val-type :db.type/ref)
        ;; Update ref vals to point to map with ident
        (update r :val list-item-vals)
        r))))

(defn project-cost-group-prices
  "Return all cost group prices for project."
  [db thk-project-id]
  (let [cost-group-keys [:db/id :cost-group/price :cost-group/project]]
    (into {}
          (comp
           (map first)

           ;; Split the map into key and value maps where the
           ;; key contains all the cost grouping attributes
           ;; and the value contains the pricing information
           (map (juxt #(apply dissoc % cost-group-keys)
                      #(select-keys % cost-group-keys))))
          (d/q '[:find (pull ?e [*])
                 :where [?e :cost-group/project ?project]
                 :in $ ?project]
               db thk-project-id))))

(defn project-cost-groups-totals
  "Summarize totals for project cost items.
  Take all components for all assets and group them by cost-grouping attributes.

  Counts quantities and total prices (if price info is known for
  a group)."
  [db thk-project-id]
  (let [items
        ;; Group by feature/component id
        (reduce
         (fn [items {:keys [id type attr val]}]
           (update items id merge {:type type attr val}))
         {}
         (cost-group-attrs-q db thk-project-id))

        types (into #{} (map (comp :type val)) items)
        type-qty-unit (into {}
                            (d/q '[:find ?e ?u
                                   :where [?e :component/quantity-unit ?u]
                                   :in $ [?e ...]]
                                 db types))
        cost-group-prices (project-cost-group-prices db thk-project-id)]
    (->>
     items

     ;; group by the same value
     (group-by val)

     ;; Determine quantity and price information for cost group
     (mapv (fn [[{type :type :as item} items]]
             (let [{p :cost-group/price :as cost-group}
                   (cost-group-prices (dissoc item :type))

                   quantity (if (= "fclass" (namespace type))
                              ;; count number of features
                              (count items)

                              ;; count the quantity field values
                              (or (ffirst
                                   (d/q '[:find (sum ?q)
                                          :where [?item :common/quantity ?q]
                                          :in $ [?item ...]]
                                        db (map key items))) 0))]
               (merge item
                      cost-group
                      {:count (count items)
                       :quantity quantity
                       :quantity-unit (type-qty-unit type)}

                      (when p
                        {:cost-per-quantity-unit p
                         :total-cost (* p quantity)}))))))))

(defn cost-item-project
  "Returns THK project id for the cost item."
  [db cost-item-id]
  (ffirst
   (d/q '[:find ?pid
          :where [?e :asset/project ?pid]
          :in $ ?e]
        db cost-item-id)))

(defn component-project
  "Returns the THK project id for the asset component."
  [db component-id]
  (loop [component (du/entity db component-id)]
    (if-let [parent-component (:component/_components component)]
      (recur parent-component)
      (cost-item-project db (get-in component [:asset/_components :db/id])))))

(defn item-type
  "Return item type, :asset or :component."
  [db id]
  (let [item (d/pull db [:asset/fclass :component/ctype] id)]
    (cond
      (:asset/fclass item)
      :asset

      (:component/ctype item)
      :component

      :else
      (throw (ex-info "Expected asset or component"
                      {:unknown-item-id id})))))

(defn next-oid
  "Get next OID for the given fclass.
  Returns vector of [update-seq-tx oid] where update-seq-tx is a
  transaction data map to update the seq# of the fclass and oid
  is the string oid for the feature."
  [db owner-code fclass]
  {:pre [(keyword? fclass)]}
  (let [{:fclass/keys [oid-prefix oid-sequence-number]}
        (d/pull db [:fclass/oid-prefix :fclass/oid-sequence-number] fclass)]
    (when-not oid-prefix
      (throw (ex-info "No OID prefix found for fclass"
                      {:fclass fclass})))
    (let [seq-number (inc (or oid-sequence-number 0))]
      [{:db/ident fclass
        :fclass/oid-sequence-number seq-number}
       (asset-model/asset-oid owner-code oid-prefix seq-number)])))

(defn next-component-oid
  "Get next OID for a new component in feature."
  [db feature-oid]
  {:pre [(asset-model/asset-oid? feature-oid)]}
  (asset-model/component-oid
   feature-oid
   (inc
    (reduce (fn [max-num [component-oid]]
              (let [[_ _ _ n] (str/split component-oid #"\-")
                    num (Long/parseLong n)]
                (max max-num num)))
            0
            (d/q '[:find ?oid
                   :where
                   [_ :asset/oid ?oid]
                   [(> ?oid ?start)]
                   [(< ?oid ?end)]
                   :in $ ?start ?end]
                 db
                 ;; Finds all OIDs for this asset
                 (str feature-oid "-")
                 (str feature-oid "."))))))
