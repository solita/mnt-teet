(ns teet.meta.meta-query
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]
            [teet.util.datomic :as du]
            [teet.user.user-model :as user-model]))

(defn gather-ids
  "Recursively gather all `:db/id` values from entities"
  ([entities]
   (gather-ids entities #{} (constantly true)))
  ([entities ids gather-entity-pred]
   (let [[ids children] (cond
                          (map? entities)
                          [(if-let [id (and (gather-entity-pred entities)
                                            (:db/id entities))]
                             (conj ids id)
                             ids)
                           (vals entities)]

                          (sequential? entities)
                          [ids entities]

                          :else
                          [ids nil])]
     (reduce (fn [ids result]
               (gather-ids result ids gather-entity-pred))
             ids
             children))))

(defn remove-entities-by-ids
  [entities ids-to-remove]
  (walk/prewalk
   (fn [entity]
     (cond
        (and (map? entity) (ids-to-remove (:db/id entity)))
        nil

        (sequential? entity)
        (filterv (fn [item]
                   (if-let [id (and (map? item) (:db/id item))]
                     (not (ids-to-remove id))
                     true))
                 entity)

        :else
        entity))
    entities))

(defn without-deleted
  "Removes all deleted entities from result.
  If keep-entity-pred is given, entity maps that satisfy the predicate
  are kept even if they are deleted."
  ([db entities]
   (without-deleted db entities (constantly false)))
  ([db entities keep-entity-pred]
   ;; Walk result tree and gather all db/id values
   (let [ids (gather-ids entities #{} (complement keep-entity-pred))
         ;; Query database for db/id deletion status
         deleted-entity-ids (into #{}
                                  (map first)
                                  (d/q '[:find ?e
                                         :where [?e :meta/deleted? true]
                                         :in $ [?e ...]]
                                       db
                                       ids))]
     ;; Walk result tree removing all deleted entities
     (remove-entities-by-ids entities deleted-entity-ids))))

(defn is-creator? [db entity user]
  (boolean
   (seq
    (d/q '[:find ?e
           :in $ ?e ?user
           :where [?e :meta/creator ?user]]
         db entity user))))

(defn entity-meta
  "Fetch meta fields about creation and modification. Also fetches
  specified extra attributes."
  [db entity & extra-attrs]
  (d/pull db (into [:meta/creator :meta/created-at
                    :meta/modifier :meta/modified-at
                    :meta/deleted?]
                   extra-attrs)
          entity))

(defn can-undo-delete?
  "Check if delete can be undone. The entity must be deleted
  within the last 5 minutes by the user."
  [db entity user]
  (let [user-id (:db/id (du/entity db (user-model/user-ref user)))
        {:meta/keys [deleted? modifier modified-at]}
        (d/pull db
                [:meta/deleted? :meta/modifier :meta/modified-at]
                entity)
        five-minutes-ago (java.util.Date. (- (System/currentTimeMillis)
                                             (* 1000 60 5)))]
    (and deleted?
         (= user-id (:db/id modifier))
         (.after modified-at five-minutes-ago))))
