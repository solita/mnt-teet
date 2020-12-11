(ns teet.util.spec
  "Utilities for spec manipulation and common specs."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def ::non-empty-string (s/and string? (complement str/blank?)))

(defn get-spec [spec]
  (try
    (s/describe spec)
    (catch #?(:clj Exception
              :cljs js/Error) _
      nil)))

(defn keys-of
  "Return the namespaced required and optional keys for an `s/keys` spec."
  [spec]
  (let [descr (s/describe spec)]
    (assert (and (coll? descr)
                 (= 'keys (first descr))))
    (let [keys (into {}
                     (map (fn [[key val]]
                            [key val]))
                     (partition 2 (rest descr)))]
      (into #{}
            (mapcat (fn [key]
                      (if (and (coll? key)
                               (= 'or (first key)))
                        (rest key)
                        [key])))
            (concat (:req keys) (:opt keys))))))


(defn coerce-dispatch [spec-description]
  (if (and (seq? spec-description) (= (first spec-description) 'and))
    (second spec-description)
    spec-description))

(defmulti coerce (fn [spec-description _value]
                   (coerce-dispatch spec-description)))

(defmethod coerce :default
  [_ value]
  value)

(defmethod coerce 'integer?
  [_ value]
  (if (integer? value)
    value
    (#?(:cljs js/parseInt
        :clj Long/parseLong)
      (str value))))

(defn coerce-by-spec
  [m]
  (reduce-kv
    (fn [m k v]
      (if-let [spec-description (get-spec k)]
        (assoc m k (coerce spec-description v))
        (assoc m k v)))
    {}
    m))

(defn select-by-spec
  "Select keys by spec, recursively walks through data
  and selects keys on maps that are included in spec.

  Only selects namespaced keys, as everything in datomic is
  namespaced.

  If keep-keys is given, also selects those keys in all levels."
  ([spec data] (select-by-spec spec data #{}))
  ([spec data keep-keys]
   (let [spec-keys (into (keys-of spec) keep-keys)]
     (into {}
           (map (fn [[key value]]
                  (let [key-spec (get-spec key)]
                    (if (and (coll? key-spec)
                             (= 'coll-of (first key-spec)))
                      [key (mapv #(select-by-spec (second key-spec) % keep-keys) value)]
                      [key value]))))
           (select-keys data spec-keys)))))

(comment
  (s/def ::foo (s/keys :req [::name ::bars]
                       :opt [::age]))

  (s/def ::name string?)
  (s/def ::bars (s/coll-of ::bar))
  (s/def ::bar (s/keys :req [::eka ::toka]))

  (def example1
    {::name "example 1"
     ::age 66
     :ignore-me "skipped"
     ::bars [{::eka 111 ::toka "heps" ::extra "won't be included"}
             {::eka 222 ::toka "kukkuu"
              :more-data "neither will this"}]}))
