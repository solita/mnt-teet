(ns teet.db-api.db-api-large-text
  "Large text support, stores large text values outside of Datomic."
  (:require [teet.integration.postgrest :as postgrest]
            [teet.util.collection :as cu]
            [teet.environment :as environment]))

(def ^:private hash-pattern #"^\[([A-Za-z0-9]+)\]$")

(defn ->hash [str]
  (when-let [[_ hash] (re-matches hash-pattern str)]
    hash))

(defn with-large-text
  "Fill in large text values in form.

  ctx contains a map with PostgREST API configuration
  large-text-keys is a set of large text keywords
  form is arbitrary nested Clojure data

  Walks form and collects and replaces all values for large-text-keys
  that contain a hash with the contents of that hash. Other values
  are left as is."

  ([large-text-keys form]
   (with-large-text (environment/api-context) large-text-keys form))
  ([ctx large-text-keys form]
   (cu/replace-deep
    (fn [x no-replacement]
      (if-let [hash (and (map-entry? x)
                         (large-text-keys (first x))
                         (->hash (second x)))]
        [(first x)
         (postgrest/rpc ctx :fetch_large_text {:hash hash})]
        no-replacement))
    form)))

(def ^{:doc "Threshold text length over which to store externally"
       :private true}
  storage-length-threshold 1024)

(defn store-large-text!
  "Store any large values for large-text-keys in PostgREST and replace them with
  their hash. If a value is already a hash, it is not touched.

  This must be called on data containing large text values before storing
  it in Datomic.

  ctx contains a map with PostgREST API configuration
  large-text-kes is a set of large text keywords
  form is arbitrary Clojure data.

  Returns updated form.
  "
  ([large-text-keys form]
   (store-large-text! (environment/api-context) large-text-keys form))
  ([ctx large-text-keys form]
   (cu/replace-deep
    (fn [x no-replacement]
      (if (and (map-entry? x)
               (large-text-keys (first x))
               (not (->hash (second x)))
               (string? (second x))
               (> (count (second x)) storage-length-threshold))
        (let [new-hash
              ;; Store new text
              (postgrest/rpc ctx :store_large_text
                             {:text (second x)})]
          [(first x) (str "[" new-hash "]")])
        no-replacement))
    form)))
