(ns teet.asset.asset-model
  "Asset model stuff

  Asset/component id pattern:
  N40-NNN-FFFFFF(-CCCCC)
  where
  N40 is owner code, default is N40 = Transport authority
  NNN is a three-letter Feature class code, shortened from the Estonian version of the feature class. The values to be provided by TA next week in the ROTL definition
  FFFFFF is a 6 digit zero padded numeric feature counter within a Feature class.

  For components:
  CCCCC is a 5 digit zero padded numeric component counter within a particular Feature."
  (:require #?(:cljs [goog.string :as gstr])
            [clojure.string :as str]))

#?(:cljs (def ^:private format gstr/format))


(def ^:private asset-pattern #"^...-\w{3}-\d{6}$")
(def ^:private component-pattern #"^...-\w{3}-\d{6}-\d{5}$")

(defn asset-oid?
  "Check if given OID refers to asset."
  [oid]
  (boolean (re-matches asset-pattern oid)))

(defn component-oid?
  "Check if given OID refers to a component."
  [oid]
  (boolean (re-matches component-pattern oid)))

(defn asset-oid
  "Format asset OID for feature class prefix and seq number."
  [owner-code fclass-oid-prefix sequence-number]
  {:post [(asset-oid? %)]}
  (format "%s-%s-%06d" owner-code fclass-oid-prefix sequence-number))

(defn component-oid
  "Format component OID for asset OID and seq number."
  [asset-oid sequence-number]
  {:post [(component-oid? %)]}
  (format "%s-%05d" asset-oid sequence-number))

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
