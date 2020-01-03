(ns teet.util.collection
  "Collection utilities")

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
