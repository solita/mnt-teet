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

(def rules
  "Helper rules for asset/component queries."
  '[;; Determine THK project match for asset/component
    [(project ?e ?project)
     [?e :asset/project ?project]]

    ;; Recursively the parent has the project
    [(project ?e ?project)
     (or [?parent :asset/components ?e]
         [?parent :component/components ?e])
     (project ?parent ?project)]


    ;; Get a location attr for entity
    [(location-attr ?e ?attr ?val)
     ;; entity has location directly, use this
     [?e :location/start-point _]
     [?e ?attr ?val]]

    [(location-attr ?e ?attr ?val)
     ;; component inherits location, use parent
     [?e :component/ctype ?ctype]
     [?ctype :component/inherits-location? true]
     (or [?parent :asset/components ?e]
         [?parent :component/components ?e])
     (location-attr ?parent ?attr ?val)]


    ;; Find the entity where location attr is missing
    ;; - entity is asset, check if it is missing
    [(location-attr-missing ?e ?attr)
     [?e :asset/fclass _]
     [(missing? $ ?e ?attr)]]

    ;; - entity does not inherit location
    [(location-attr-missing ?e ?attr)
     [?e :component/ctype ?ctype]
     [(missing? $ ?ctype :component/inherits-location?)]
     [(missing? $ ?e ?attr)]]

    ;; - entity inherits location, check if parent is missing
    [(location-attr-missing ?e ?attr)
     [?e :component/ctype ?ctype]
     [?ctype :component/inherits-location? true]
     (or [?parent :asset/components ?e]
         [?parent :component/components ?e])
     (location-attr-missing ?parent ?attr)]])

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

(def material-pattern
  '[*
    {:material/fgroups
     [*]}
    {:attribute/_parent
     [*
      {:enum/_attribute [*]}]}])

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

(defn last-atl-modification-time [db]
  (ffirst
   (d/q '[:find (max ?txi)
          :where
          [_ :tx/schema-imported-at _ ?tx]
          [?tx :db/txInstant ?txi]] db)))


(defn asset-type-library [db]
  (walk/postwalk
   (fn [x]
     (->> x
          remove-empty-selection-attributes
          (pull-child-ctypes db)))
   {:tx/schema-imported-at (ffirst
                            (d/q '[:find (max ?t)
                                   :where [_ :tx/schema-imported-at ?t]]
                                 db))
    :ctype/component (d/pull db ctype-pattern :ctype/component)
    :ctype/feature (d/pull db ctype-pattern :ctype/feature)
    :ctype/material (d/pull db ctype-pattern :ctype/material)
    :ctype/location (d/pull db ctype-pattern :ctype/location)
    :fgroups (mapv first
                   (d/q '[:find (pull ?fg p)
                          :where [?fg :asset-schema/type :asset-schema.type/fgroup]
                          :in $ p]
                        db type-library-pattern))
    :materials (mapv first
                     (d/q '[:find (pull ?m p)
                            :where [?m :asset-schema/type :asset-schema.type/material]
                            :in $ p]
                          db material-pattern))}))


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
        (d/q {:query '{:find [?oid]
                       :where [[?asset :asset/project ?project]
                               [?asset :asset/oid ?oid]]
                       :in [$ ?project]}
              :args [db thk-project-id]})))

(defn project-assets-and-components-matching-road
  "Find OIDs of all project assets and components that match a given road.
  If component inherits location from parent, the parent component location is used."
  [db thk-project-id road-nr]
  (mapv first
        (d/q '[:find ?oid
               :where
               (project ?e ?project)
               (location-attr ?e :location/road-nr ?road-nr)
               [?e :asset/oid ?oid]
               :in $ % ?project ?road-nr]
             db rules thk-project-id road-nr)))

(defn project-assets-and-components-with-road
  "Find OIDs of all project assets and components that have a road defined."
  [db thk-project-id]
  (mapv first
        (d/q '[:find ?oid
               :where
               (project ?e ?project)
               (location-attr ?e :location/road-nr _)
               [?e :asset/oid ?oid]
               :in $ % ?project]
             db rules thk-project-id)))

(defn project-assets-and-components
  "Find OIDs of all project assets and components."
  [db thk-project-id]
  (into []
        (mapcat (fn [[asset-oid]]
                  (concat (list asset-oid)
                          (filter
                           #(not (asset-model/material-oid? %))
                           (asset-component-oids db asset-oid)))))
        (d/q '[:find ?oid
               :where
               [?e :asset/project ?project]
               [?e :asset/oid ?oid]
               :in $ % ?project]
             db rules thk-project-id)))

(defn project-assets-and-components-without-road
  "Find OIDs of all project assets and components where the road value is missing."
  [db thk-project-id]
  (mapv first
        (d/q '[:find ?oid
               :where
               (project ?e ?project)
               (location-attr-missing ?e :location/road-nr)
               [?e :asset/oid ?oid]
               :in $ % ?project]
             db rules thk-project-id)))

(defn project-asset-and-component-oids
  "Return all OIDs of assets and their components for the given THK project."
  [db thk-project-id]
  (mapcat #(into [%] (asset-component-oids db %))
          (project-asset-oids db thk-project-id)))

(defn- cost-group-attrs-q
  "Return all items in project with type, status and cost grouping attributes.
  Returns map where key is item id and value is map of the values.
  Only returns items that have some cost grouping attributes.

  List of OID codes (`oids`) determines what assets/components to return."
  [db oids]
  (let [entity-attr-vals
        (d/q '[:find ?a ?type-ident ?attr-ident ?val ?value-type-ident
               :keys id type attr val val-type
               :where
               [?a :asset/oid ?oid]
               (or
                [?a :asset/fclass ?type]
                [?a :component/ctype ?type])
               [?a ?attr ?val]
               (or [?attr :attribute/cost-grouping? true]
                   [?attr :db/ident :common/status]
                   [?attr :db/ident :location/road-nr])
               [?attr :db/ident ?attr-ident]
               [?type :db/ident ?type-ident]
               [?attr :db/valueType ?value-type]
               [?value-type :db/ident ?value-type-ident]
               :in $ [?oid ...]]
             db oids)

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


    (cu/keep-vals
     ;; Keep only items that have cost grouping fields
     ;; not just type, status amd road
     (fn [v]
       (when (seq (dissoc v :type :common/status :location/road-nr))
         v))
     ;; Group by feature/component id
     (reduce
      (fn [items {:keys [id type attr val val-type]}]
        (update items id merge
                {:type type
                 attr (if (= val-type :db.type/ref)
                        ;; Update ref vals to point to map with ident
                        (list-item-vals val)
                        val)}))
      {}
      entity-attr-vals))))

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
  a group).

  Optional `oids` lists OID codes to use, if omitted all project asset/component
  OIDs are considered."
  [db thk-project-id & [oids]]
  (let [items (cost-group-attrs-q
               db (if (some? oids)
                    oids
                    (project-asset-and-component-oids db thk-project-id)))
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
                                          :where [?item :component/quantity ?q]
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

(defn material-project
  "Returns the THK project id for the material."
  [db material-id]
  (let [material (du/entity db material-id)]
    (when-let [component-id (-> material :component/_materials :db/id)]
      (component-project db component-id))))

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

(defn max-component-oid-number
  "Get max OID sequence number for a component in asset.
  If the asset has no components yet, returns zero."
  [db feature-oid]
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
               (str feature-oid "."))))

(defn next-component-oid
  "Get next OID for a new component in feature."
  [db feature-oid]
  {:pre [(asset-model/asset-oid? feature-oid)]}
  (asset-model/component-oid
   feature-oid
   (inc (max-component-oid-number db feature-oid))))

(defn next-material-oid
  "Get next OID for a new material in component."
  [db component-oid]
  {:pre [(asset-model/component-oid? component-oid)]}
  (asset-model/material-oid
   component-oid
   (inc
    (reduce (fn [max-num [component-oid]]
              (let [[_ _ _ _ n] (str/split component-oid #"\-")
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
                 ;; Finds all material OIDs for this component
                 (str component-oid "-")
                 (str component-oid "."))))))

(defn project-boq-version
  "Fetch the latest BOQ version entity for the given THK project."
  [db thk-project-id]
  (ffirst
   (d/q '[:find (pull ?e [*])
          :where
          [?e :boq-version/project ?project]
          [?e :boq-version/created-at ?at]
          (not-join [?project ?at]
                    [?newer-lock :boq-version/project ?project]
                    [?newer-lock :boq-version/created-at ?newer-at]
                    [(> ?newer-at ?at)])
          :in $ ?project]
        db thk-project-id)))

(defn project-boq-version-history
  "Return all BOQ project versions (excluding unlocked versions)
  for the given THK project"
  [db thk-project-id]
  (->>
   (d/q '[:find (pull ?e [*])
          :where
          [?e :boq-version/project ?project]
          [?e :boq-version/locked? true]
          :in $ ?project] db thk-project-id)
   (map first)
   (sort-by :boq-version/created-at)
   reverse))

(defn latest-change-in-project
  "Fetch latest timestamp and author UUID that has changed anything in the given THK project."
  [db thk-project-id]
  (first
   (d/q '[:find ?max-txi ?author
          :where
          ;; Find the max tx time for any change in the given project
          [(q '[:find (max ?txi)
                :where
                (or [?e :asset/project ?project]
                    [?e :cost-group/project ?project]
                    [?e :boq-version/project ?project])
                [?e _ _ ?tx]
                [?tx :db/txInstant ?txi]
                :in $ ?project] $ ?project) [[?max-txi]]]

          ;; Get the tx and its author
          [?tx :db/txInstant ?max-txi]
          [?tx :tx/author ?author]
          :in $ ?project]
        db thk-project-id)))

(defn version-db
  "Return db as of the given version id."
  [db version-id]
  (let [c (:boq-version/created-at (du/entity db version-id))]
    (if-not c
      (throw (ex-info "No version creation time found"
                      {:version-id version-id}))
      (d/as-of db c))))

(defn leaf-component?
  "Is the component a leaf component? A component is a leaf component if
  the component type does not allow child components."
  [db component-id]
  ;; Does there exist a ?ctype such that
  ;; - ?ctype is the ctype of component-id and
  ;; - ?ctype is no other ctype's parent
  (->> (d/q '[:find (some? ?ctype)
              :where
              [?cid :component/ctype ?ctype]
              (not [_ :ctype/parent ?ctype])
              :in $ ?cid]
            db component-id)
       ffirst
       boolean))
