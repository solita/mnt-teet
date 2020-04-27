(ns teet.meta.meta-model
  "Transaction functions for meta data"
  (:require [teet.user.user-model :as user-model])
  (:import (java.util Date)))

(defn creation-meta
  [user]
  {:pre [(user-model/user-ref user)]}
  {:meta/creator    (user-model/user-ref user)
   :meta/created-at (Date.)})

(defn modification-meta
  [user]
  {:pre [(user-model/user-ref user)]}
  {:meta/modifier    (user-model/user-ref user)
   :meta/modified-at (Date.)})

(defn deletion-tx
  [user id]
  (assoc (modification-meta user)
    :meta/deleted? true
    :db/id id))
