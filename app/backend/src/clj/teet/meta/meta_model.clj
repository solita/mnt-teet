(ns teet.meta.meta-model
  "Transaction functions for meta data"
  (:require [teet.user.user-model :as user-model])
  (:import (java.util Date)))

(defn creation-meta
  [user]
  {:pre [(user-model/user-ref user)]}
  {:meta/creator    (user-model/user-ref user)
   :meta/created-at (Date.)})

(defn system-created
  []
  {:meta/created-at (Date.)})

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

(defn with-creation-or-modification-meta
  "Add creation or modification meta to entity.
  If entity has a string :db/id then creation meta
  attributes will be added. For other :db/id values
  the modification meta attributes will be added.

  If :db/id is missing, throws exception."
  [user entity]
  (if-not (contains? entity :db/id)
    (throw (ex-info "Can't determine what metadata to add, no :db/id"
                    {:entity entity}))
    (if (string? (:db/id entity))
      (merge entity (creation-meta user))
      (merge entity (modification-meta user)))))
