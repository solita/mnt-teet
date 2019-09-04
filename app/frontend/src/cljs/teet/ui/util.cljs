(ns teet.ui.util
  "Small utilities for reagent")

(defn with-keys
  "doall children-seq and ensure every child has a unique key.
  If the child items have no key in metadata, an index based key
  will be added"
  [children-seq]
  (doall
   (map-indexed (fn [i child]
                  (if (contains? (meta child) :key)
                    child
                    (with-meta child
                      {:key i})))
                children-seq)))
