(ns teet.util.datomic
  "Datomic query/transaction utility functions."
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            #?(:clj [datomic.client.api :as d])
            [clojure.set :as set]
            [teet.util.collection :as cu]
            [clojure.string :as str]
            [teet.log :as log]))

(s/def :db/ident keyword?)
(s/def ::enum (s/or :keyword keyword?
                    :map-with-db-ident (s/keys :req [:db/ident])))

(defn id=
  "Compare two :db/id values.

  Compared as strings on the frontend because Datomic ids may be bigger than can be represented
  in JS Number. They can be native numbers or goog.math.Long instances."
  [id1 id2]
  #?(:clj (= id1 id2)
     :cljs (= (str id1) (str id2))))

(defn find-by-id
  "Find first element in `collection` whose `:db/id` is `id`"
  [id collection]
  (cu/find-first (comp (partial id= id) :db/id)
                 collection))

(defn enum->kw
  "Accept enum map or keyword or `nil`, return keyword, or `nil` if argument is `nil`"
  [enum-map-or-kw]
  {:pre [(or (nil? enum-map-or-kw)
             (s/valid? ::enum enum-map-or-kw))]}
  (when enum-map-or-kw
    (if (keyword? enum-map-or-kw)
      enum-map-or-kw
      (:db/ident enum-map-or-kw))))

(defn enum=
  "Compare two enum values.
  Enum may be a keyword or a map containing :db/ident, or `nil`."
  [e1 e2]
  (= (enum->kw e1)
     (enum->kw e2)))

(defn changed-entity-ids
  "Returns all :db/id values of entities that were \"changed\" by the given transaction."
  [tx-result]
  (let [tx-id (get-in tx-result [:tempids "datomic.tx"])]
    (into #{}
          (keep (fn [datom]
                  (let [id (nth datom 0)]
                    (when (not= id tx-id)
                      id))))
          (:tx-data tx-result))))

#?(:clj
   (defn retractions
     "Return transaction data that retracts the current values of
  given keys in the entity."
     [db eid keys-to-retract]
     (assert (some? eid) "Must specify entity id")
     (let [{id :db/id :as values}
           (d/pull db (into [:db/id] keys-to-retract) eid)]
       (vec
        (for [[k v] values
              :when (and (not= k :db/id)
                         (contains? keys-to-retract k))]
          [:db/retract id k v])))))

#?(:clj
   (do
     (declare entity)
     (defn- transform-entity-value [db val]
       (cond
         (and (map? val)
              (contains? val :db/id))
         (entity db (:db/id val) val)

         (vector? val)
         (mapv (partial transform-entity-value db) val)

         :else
         val))

     (defn- fetch-entity-attr! [db eid fetched-attrs attr default-value]
       (let [attrs
             (swap! fetched-attrs
                    (fn [fetched-attrs]
                      (if (contains? fetched-attrs attr)
                        fetched-attrs
                        (reduce-kv
                         (fn [attrs k v]
                           (if (contains? attrs k)
                             attrs
                             (assoc attrs k
                                    (transform-entity-value db v))))
                         fetched-attrs
                         (d/pull db [attr] eid)))))]
         (get attrs attr default-value)))
     (deftype Entity [db eid fetched-attrs]
       java.lang.Object
       (toString [_]
         (str "#<Entity, eid: " eid
              ", fetched-attrs: " (pr-str @fetched-attrs) ">"))

       clojure.lang.ILookup
       (valAt [_ kw]
         (assert (keyword? kw))
         (fetch-entity-attr! db eid fetched-attrs kw nil))
       (valAt [_ kw default-value]
         (assert (keyword? kw))
         (fetch-entity-attr! db eid fetched-attrs kw default-value)))

     (defmethod print-method Entity [entity writer]
       (print-simple entity writer))
     (defn entity
       "Returns a navigable entity that lazily fetches attributes."
       ([db eid]
        (entity db eid (if (number? eid)
                         {:db/id eid}
                         {})))
       ([db eid fetched-attrs]
        (assert eid "entity requires an eid")
        (Entity. db eid (atom (cu/map-vals
                               (partial transform-entity-value db)
                               fetched-attrs)))))))

(defn db-ids
  "Recursively gather non-string :db/id values of form.
  Considers all maps that have a :db/id excluding enum references
  (maps that contain :db/ident key)"
  [form]
  (cond
    (map? form)
    (let [id (when (not (contains? form :db/ident))
               (:db/id form ::not-found))
          acc (if (and id
                       (not= ::not-found id)
                       (not (string? id)))
                #{id}
                #{})]
      (reduce set/union
              acc
              (map db-ids (vals form))))

    (sequential? form)
    (reduce set/union
            #{}
            (map db-ids form))

    :else
    #{}))

(defn no-new-db-ids?
  "Check that there are no new ids in the right set of ids"
  [left right]
  (set/subset?
    (db-ids right)
    (db-ids left)))

(defn idents->keywords [m]
  (walk/prewalk
   (fn walk-fn [x]
     (if (and (map? x) (contains? x :db/ident))
       (:db/ident x)
       x))
   m))

(defn- refs->id
  "Turn maps with {:db/id <num>} into just <num> values."
  [m]
  (cu/map-vals
   #(if-let [id (and (map? %) (:db/id %))]
      id
      %)
   m))

(defn modify-entity-tx
  "Create transaction data that asserts new changed attributes
  and retracts attributes that are no longer present.
  If there are no changes between old and new entity, returns empty vector."
  [old-entity {id :db/id :as new-entity}]
  (assert (= (:db/id old-entity) id)
          "This is not the same entity (different :db/id values)")
  (let [old-entity (refs->id old-entity)
        new-entity (refs->id new-entity)
        retractions
        (for [[k v] old-entity
              :when (not (contains? new-entity k))]
          [:db/retract id k v])
        changes (into {}
                      (filter (fn [[k v]]
                                (not= v (get old-entity k))))
                      new-entity)]
    (if (or (seq retractions)
            (seq changes))
      (vec
       (concat retractions [(assoc changes :db/id id)]))
      [])))

#?(:clj
   (defn modify-entity-retract-nils
     "Create transaction data that asserts updated entity map
     and retracts any attribute whose value is nil.

     If entity :db/id is not a number, no retractions are issued."
     [db {id :db/id :as new-entity}]
     (let [entity-values (cu/without-nils new-entity)
           nil-attributes (into []
                                (comp
                                 (filter (comp nil? val))
                                 (map key))
                                new-entity)]
       (into [entity-values]
             (when (number? id)
               (for [[attr val] (d/pull db nil-attributes id)]
                 [:db/retract id attr val]))))))

#?(:clj
   (do
     (defn- symbols [form]
       (cu/collect symbol? form))

     (defn- map-by-keywords [[first & rest]]
       (when first
         (if-not (keyword? first)
           (throw (ex-info "Expected keyword"
                           {:got first}))
           (merge {first (take-while (complement keyword?) rest)}
                  (map-by-keywords (drop-while (complement keyword?) rest))))))

     (defn- to-map-query
       "Convert multi arg query call to map format"
       [args]
       (merge
        {:in '[$]}
        (cond
          (and (= 1 (count args))
               (map? (first args)))
          (let [{:keys [query args]} (first args)]
            ;; Already a map, take query and args
            (merge
             (if (map? query)
               query
               (map-by-keywords query))
             {:args args}))

          (and (map? (first args))
               (contains? (first args) :find))
          ;; Map query with args
          (merge {:args (vec (rest args))}
                 (first args))

          (vector? (first args))
          ;; Split by keyword
          (merge {:args (vec (rest args))}
                 (map-by-keywords (first args)))

          :else
          (throw (ex-info "Invalid query call, expected single arg query map, query map with args or vector as first argument."
                          {:invalid-arguments args})))))

     (defn- assert-valid-query [query-args]
       (let [{:keys [in args find where] :as q} (to-map-query query-args)]
         (when-not (= (count in) (count args))
           (throw (ex-info ":in list must be same length as args"
                           {:declared-argument-count (count in)
                            :declared-arguments in
                            :received-argument-count (count args)
                            :received-arguments args})))

         (let [unreferred-symbols (set/difference
                                   (symbols in)
                                   #{'$ '... '%}
                                   (set/union (symbols find)
                                              (symbols where)))]
           (when (seq unreferred-symbols)
             (throw (ex-info "Unreferred to input binding symbols"
                             {:unreferred-symbols unreferred-symbols}))))))

     (def datomic-q d/q)
     (defn q
       "Wrapper to d/q that asserts some validity rules."
       [& args]
       (assert-valid-query args)
       (let [start (System/currentTimeMillis)]
         (try
           (apply datomic-q args)
           (finally
             (let [end (System/currentTimeMillis)]
               (when (> (- end start) 1000)
                 (log/debug "SLOW QUERY TAKING OVER 1s" (first args))))))))))
