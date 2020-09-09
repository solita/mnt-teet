(ns teet.ui.authorization-context
  (:require [teet.ui.context :as context]))

(defn consume [component-fn]
  (context/consume :authorization component-fn))

(defn with [added-rights child-component]
  (context/update-context :authorization
                          #(into (or % #{}) added-rights)
                          child-component))

(defn when-authorized [right child-component]
  (context/consume :authorization
                   (fn [rights]
                     (when ((or rights #{}) right)
                       child-component))))
