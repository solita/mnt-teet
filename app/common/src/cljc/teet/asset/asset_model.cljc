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
