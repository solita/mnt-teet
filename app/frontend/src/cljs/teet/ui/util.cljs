(ns teet.ui.util
  "Small utilities for reagent"
  (:require ["react" :as react]))

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

(defn merge-props
  "Normal merge, except :class is merged by joining with space"
  [a b]
  (-> a
      (merge (dissoc b :class))
      (update :class #(if-let [b-class (:class b)]
                        (str (when % (str % " ")) b-class)
                        %))))

(defn make-component [component props]
  (fn [& children]
    (if (map? (first children))
      (into [component (merge-props props (first children))] (rest children))
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

(deftype UseStateAtom [state set-state!]
  IAtom
  IDeref
  (-deref [_] state)

  IReset
  (-reset! [_ new-value]
    (set-state! new-value))

  ISwap
  (-swap! [_ f] (set-state! (f state)))
  (-swap! [_ f x] (set-state! (f state x)))
  (-swap! [_ f x y] (set-state! (f state x y)))
  (-swap! [_ f x y more] (set-state! (apply f state x y more))))

(defn use-state-atom [val]
  (let [[state set-state!] (react/useState val)]
    (->UseStateAtom state set-state!)))

(defn use-effect [effect-fn & deps]
  (react/useEffect effect-fn (into-array deps)))

(def no-cleanup (constantly nil))
