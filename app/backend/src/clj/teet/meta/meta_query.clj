(ns teet.meta.meta-query
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]))

(defn gather-ids
  "Recursively gather all `:db/id` values from entities"
  ([entities]
   (gather-ids entities #{}))
  ([entities ids]
   (let [[ids children] (cond
                          (map? entities)
                          [(if-let [id (:db/id entities)]
                             (conj ids id)
                             ids)
                           (vals entities)]

                          (vector? entities)
                          [ids entities]

                          :else
                          [ids nil])]
     (reduce (fn [ids result]
               (gather-ids result ids))
             ids
             children))))

(defn remove-entities-by-ids
  [entities ids-to-remove]
  (walk/prewalk
    (fn [entity]
      (cond
        (and (map? entity) (ids-to-remove (:db/id entity)))
        nil

        (vector? entity)
        (filterv (fn [item]
                   (if-let [id (and (map? item) (:db/id item))]
                     (not (ids-to-remove id))
                     true))
                 entity)

        :else
        entity))
    entities))

(defn without-deleted
  "Removes all deleted entities from result"
  [db entities]
  ;Walk result tree and gather all db/id values
  (let [ids (gather-ids entities)
        ;Query database for db/id deletion status
        deleted-entity-ids (into #{}
                                 (map first)
                                 (d/q '[:find ?e
                                        :where [?e :meta/deleted? true]
                                        :in $ [?e ...]]
                                      db
                                      ids))]
    ;Walk result tree removing all deleted entities
    (remove-entities-by-ids entities deleted-entity-ids)))

;[{:db/id 1
;  :task/documents [{:db/id 2}]}] -> #{1 2} -> '[:find ?e
;                                                :where [?e :meta/deleted? true]
;                                                :in $ ?e ...]
