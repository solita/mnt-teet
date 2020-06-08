(ns teet.util.datomic
  "Datomic query/transaction utility functions."
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            #?(:clj [datomic.client.api :as d])
            [clojure.set :as set]))

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

(defn enum=
  "Compare two enum values.
  Enum may be a keyword or a map containing :db/ident."
  [e1 e2]
  (let [v1 (if (keyword? e1)
             e1
             (:db/ident e1))
        v2 (if (keyword? e2)
             e2
             (:db/ident e2))]
    (= v1 v2)))

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
         (entity db (:db/id val))

         (vector? val)
         (mapv (partial transform-entity-value db) val)

         :else
         val))

     (defn- fetch-entity-attr! [db eid fetched-attrs attr default-value]
       (let [v (get
                (swap! fetched-attrs
                       (fn [fetched-attrs]
                         (if (contains? fetched-attrs attr)
                           fetched-attrs
                           (assoc fetched-attrs attr
                                  (transform-entity-value
                                   db (get (d/pull db [attr] eid)
                                           attr ::not-found))))))
                             attr)]
                      (if (not= ::not-found v)
                        v
                        default-value)))
     (deftype Entity [db eid fetched-attrs]
       java.lang.Object
       (toString [_]
         (str "#<Entity, eid: " eid ">"))

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
       [db eid]
       (Entity. db eid (atom (if (number? eid)
                               {:db/id eid}
                               {}))))))

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
