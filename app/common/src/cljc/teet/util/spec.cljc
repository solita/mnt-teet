(ns teet.util.spec
  (:require [clojure.spec.alpha :as s]))



(defn get-spec [spec]
  (try
    (s/describe spec)
    (catch Exception _
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


(defn select-by-spec
  "Select keys by spec, recursively walks through data
  and selects keys on maps that are included in spec.

  Only selects namespaced keys, as everything in datomic is
  namespaced."
  [spec data]
  (let [spec-keys (keys-of spec)]
    (into {}
          (map (fn [[key value]]
                 (let [key-spec (get-spec key)]
                   (if (and (coll? key-spec)
                            (= 'coll-of (first key-spec)))
                     [key (mapv #(select-by-spec (second key-spec) %) value)]
                     [key value]))))
          (select-keys data spec-keys))))

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
