(ns teet.util.datomic
  "Datomic query/transaction utility functions."
  (:require [clojure.spec.alpha :as s]
            #?(:clj [datomic.client.api :as d])))

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
        v2 (if (keyword e2)
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
