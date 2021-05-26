(ns teet.asset.assets-controller
  (:require [teet.util.collection :as cu]))

(defn- to-set
  ([filters key] (to-set filters key :db/ident))
  ([filters key item-fn]
   (cu/update-in-if-exists
    filters [key]
    (fn [items]
      (let [set (into #{} (map item-fn) items)]
        (when-not (empty? set)
          set))))))

(defn assets-query [filters]
  (let [criteria (-> filters
                     (to-set :fclass (comp :db/ident second))
                     (to-set :common/status)
                     cu/without-nils)]
    (when (seq criteria)
      {:query :assets/search
       :args criteria})))
