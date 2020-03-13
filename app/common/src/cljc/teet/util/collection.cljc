(ns teet.util.collection
  "Collection utilities")

(defn contains-value? [coll v]
  (some #(when (= % v) true) coll))

(defn count-by
  "Group by and count.
  Returns map from each return value of group-fn to number of occurances."
  [group-fn coll]
  (reduce (fn [counts v]
            (update counts (group-fn v) (fnil inc 0)))
          {} coll))

(defn without-nils
  "Nonrecursively remove keys with nil values"
  [m]
  (reduce-kv (fn [m k v]
               (if (some? v)
                 (assoc m k v)
                 m))
             {}
             m))

(defn nil-keys
  "Returns set of keys that are nil in map"
  [m]
  (into #{}
        (keep (fn [[k v]]
                (when (nil? v)
                  k)))
        m))

(defn find-first
  "Find first element in `collection` matching `predicate`"
  [predicate collection]
  (some (fn [element]
          (when (predicate element)
            element))
        collection))

(defn find-idx
  "Find index of first element in `collection` matching `predicate`."
  [predicate collection]
  (first
   (keep-indexed
    (fn [i element]
      (when (predicate element)
        i))
    collection)))

(defn toggle
  "Toggle value membership in set.
  If set is nil, it is initialized to an empty set so
  it is safe to call this for an uninitialized set."
  [set value]
  (let [set (or set #{})]
    (if (set value)
      (disj set value)
      (conj set value))))
