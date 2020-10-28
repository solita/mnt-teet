(ns teet.util.collection
  "Collection utilities"
  (:require [clojure.walk :as walk]))

(defn contains-value? [coll v]
  (some #(when (= % v) true) coll))

(defn count-by
  "Group by and count.
  Returns map from each return value of group-fn to number of occurances."
  [group-fn coll]
  (reduce (fn [counts v]
            (update counts (group-fn v) (fnil inc 0)))
          {} coll))

(defn count-matching
  "Count the amount of items in `coll` matching `pred`"
  [pred coll]
  (reduce +
          (for [item coll]
            (if (pred item)
              1
              0))))

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



(defn remove-by-id
  "Remove elements in `collection` whose `:db/id` is `id`"
  ([id]
   (remove #(= (:db/id %) id)))
  ([id collection]
   (remove #(= (:db/id %) id) collection)))

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

(defn- update-path* [[path & rest-path] update-fn update-args here]
  (cond
    ;; Done traversing update the value here
    (nil? path)
    (apply update-fn here update-args)

    (fn? path)
    (mapv (fn [item]
            (if (path item)
              (update-path* rest-path update-fn update-args item)
              item)) here)

    :else
    (update here path (partial update-path* rest-path update-fn update-args))))

(defn update-path
  "Generic deep update. Like update-in but more powerful.

  Here is the deeply nested structure that needs update.

  Path-spec is a vector of path components.

  If a path-component is a function, it is a predicate
  that only updates entries in a sequential context
  that match the predicate.

  Anything else is considered an associative key (like
  keywords, numbers for vectors)."
  [here path-spec update-fn & update-args]
  (update-path* path-spec update-fn update-args here))


(defn find->
  "Finds item in paths. Like some-> but more powerful.

  Here is a deeply nested structure to find something in.

  Paths is a list of path components to traverse.
  Path components are either keys to get from the current
  value or predicates to filter it with."
  [here & paths]
  (let [path (first paths)]
    (cond
      (nil? path)
      here

      (fn? path)
      (some (fn [item]
              (when (path item)
                (apply find-> item (rest paths)))) here)

      :else
      (apply find-> (get here path) (rest paths)))))

(defn map-keys
  "Given map `m` returns a map where each key `k` is replaced by `(f k)`"
  [f m]
  {:pre [(map? m)]}
  (into {}
        (for [[k v] m]
          [(f k) v])))

(defn map-vals
  "Given map `m` returns a map where each value `v` is replaced by `(f v)`"
  [f m]
  {:pre [(map? m)]}
  (into {}
        (for [[k v] m]
          [k (f v)])))

(defn contains-in?
  [m ks]
  (not= ::absent (get-in m ks ::absent)))

(defn update-in-if-exists
  [m ks f & args]
  (if (contains-in? m ks)
    (apply (partial update-in m ks f) args)
    m))

(defn deep-merge
  "Recursive merge-with merge."
  [a b]
  (if (and (map? a) (map b))
    (merge-with deep-merge a b)
    b))

(defn collect
  "Walk form and collect all items that match pred.
  Retuns set of items."
  [pred form]
  (let [items (volatile! (transient #{}))]
    (walk/prewalk (fn [x]
                    (when (pred x)
                      (vswap! items conj! x))
                    x)
                  form)
    (-> items deref persistent!)))

(defn replace-deep
  "Walk form and replace any items in replacements.
  Returns updated form."
  [replacements form]
  (walk/postwalk
   (fn [x]
     (let [replacement (replacements x ::no-replacement)]
       (if (= replacement ::no-replacement)
         x
         replacement)))
   form))
