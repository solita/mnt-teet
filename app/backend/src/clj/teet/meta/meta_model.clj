(ns teet.meta.meta-model
  "Transaction functions for meta data"
  (:import (java.util Date)))

(defn creation-meta
  [{id :db/id :as _user}]
  {:meta/creator    id
   :meta/created-at (Date.)})

(defn modification-meta
  [{id :user/id :as _user}]
  {:meta/modifier    [:user/id id]
   :meta/modified-at (Date.)})

(defn deletion-tx
  [user id]
  (assoc (modification-meta user)
    :meta/deleted? true
    :db/id id))

