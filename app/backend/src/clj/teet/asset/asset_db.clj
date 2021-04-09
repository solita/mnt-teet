(ns teet.asset.asset-db
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]
            [teet.util.datomic :as du]
            [teet.util.collection :as cu]
            [clojure.string :as str]))

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
  [db fclass]
  {:pre [(keyword? fclass)]}
  (let [{:fclass/keys [oid-prefix oid-sequence-number]}
        (d/pull db [:fclass/oid-prefix :fclass/oid-sequence-number] fclass)]
    (when-not oid-prefix
      (throw (ex-info "No OID prefix found for fclass"
                      {:fclass fclass})))
    (let [seq-number (inc (or oid-sequence-number 0))]
      [{:db/ident fclass
        :fclass/oid-sequence-number seq-number}
       (format "N40-%s-%06d" oid-prefix seq-number)])))

(defn next-component-oid
  "Get next OID for a new component in feature."
  [db feature-oid]
  {:pre (string? feature-oid)}
  (format "%s-%05d"
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
