(ns teet.ui.util
  "Small utilities for reagent")

(defn has-key? [component]
  (contains? (meta component) :key))

(defn with-keys
  "doall children-seq and ensure every child has a unique key.
  If the child items have no key in metadata, an index based key
  will be added"
  [children-seq]
  (doall
   (map-indexed (fn [i child]
                  (if (has-key? child)
                    child
                    (with-meta child
                      {:key i})))
                children-seq)))

(defn make-component [component props]
  (fn [& children]
    (if (map? (first children))
      (into [component (merge props (first children))] (rest children))
      (into [component (or props {})] children))))

(defn mapc
  "Map component. Like map but runs doall and adds keys to to result.
  If the input arguments to the component-fn contain a :db/id it will
  be added as a key if the returned component does not have a key."
  [component-fn & collections]
  (with-keys
    (apply map
           (fn [& args]
             (let [component (apply component-fn args)]
               (if (has-key? component)
                 ;; Component already has key, return it as is
                 component

                 ;; Try to add :db/id key found in arguments as the key
                 (if-let [id (some :db/id args)]
                   (with-meta component
                     {:key (str id)})
                   component))))
           collections)))

(defn lookup-tracker
  "UI debug helper to see what is being looked up"
  [log-prefix obj]
  (let [get-key (fn [k not-found]
                  (js/console.log log-prefix (pr-str k))
                  (get obj k not-found))]
    (reify cljs.core/ILookup
      (-lookup [_ k]
        (get-key k nil))
      (-lookup [_ k not-found]
        (get-key k not-found)))))
