(ns teet.asset.asset-model
  "Asset model stuff"
  (:require #?(:cljs [goog.string :as gstr])
            [clojure.string :as str]))

#?(:cljs (def ^:private format gstr/format))

(defn asset-oid
  "Format asset OID for feature class prefix and seq number."
  [fclass-oid-prefix sequence-number]
  (format "N40-%s-%06d" fclass-oid-prefix sequence-number))

(defn component-oid
  "Format component OID for asset OID and seq number."
  [asset-oid sequence-number]
  (format "%s-%05d" asset-oid sequence-number))

(def ^:private asset-pattern #"^N40-\w{3}-\d{6}$")
(def ^:private component-pattern #"^N40-\w{3}-\d{6}-\d{5}$")

(defn asset-oid?
  "Check if given OID refers to asset."
  [oid]
  (boolean (re-matches asset-pattern oid)))

(defn component-oid?
  "Check if given OID refers to a component."
  [oid]
  (boolean (re-matches component-pattern oid)))

(defn component-asset-oid
  "Given component OID, return the OID of the parent asset."
  [component-oid]
  {:pre [(component-oid? component-oid)]}
  (-> component-oid
      (str/split #"-")
      butlast
      (->> (str/join "-"))))

(defn find-component-path
  "Return vector containing all parents of component from asset to the component.
  For example:
  [a c1 c2 c3]
  where a is the asset, that has component c1
  c1 has child component c2
  and c2 has child component c3 (the component we want)"
  [asset component-oid]
  (let [containing
        (fn containing [path here]
          (let [cs (concat (:asset/components here)
                           (:component/components here))]
            (if-let [c (some #(when (= component-oid (:asset/oid %))
                                %) cs)]
              ;; we found the component at this level
              (into path [here c])

              ;; not found here, recurse
              (first
               (for [sub cs
                     :let [sub-path (containing (conj path here) sub)]
                     :when sub-path]
                 sub-path)))))]

    (containing [] asset)))
