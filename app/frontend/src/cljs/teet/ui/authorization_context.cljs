(ns teet.ui.authorization-context
  (:require [teet.ui.context :as context]))

(defn consume [component-fn]
  (context/consume :authorization component-fn))

(defn with
  "Return child-component in a context that has added rights.
  If added-rights is a set, those rights are directly added
  to the context.

  If added-rights is a map from keyword to truthy values,
  the keys are conjoined/disjoined from the context based
  on their value."
  [added-rights child-component]
  (context/update-context
   :authorization
   (fn [current-rights-set]
     (let [rights (or current-rights-set #{})]
       (if (map? added-rights)
         ;; Disj/conj based on value
         (reduce-kv (fn [r k v]
                      (if v
                        (conj r k)
                        (disj r k)))
                    rights added-rights)

         ;; Otherwise add new rights as is
         (into rights added-rights))))
   child-component))

(defn when-authorized [right child-component]
  (context/consume :authorization
                   (fn [rights]
                     (when ((or rights #{}) right)
                       child-component))))
