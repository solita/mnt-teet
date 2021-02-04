(ns teet.entity.entity-db
  "Generic entity database utilities that apply to many different
  types of entities."
  (:require [datomic.client.api :as d]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]))

(defn entity-by-teet-id
  "Return entity :db/id for the given :teet/id UUID.
  Checks that the entity has a required attribute."
  [required-attribute-kw db teet-id]
  (ffirst
   (d/q [:find '?e
         :where
         ['?e :teet/id '?id]
         ['?e required-attribute-kw '_]
         :in '$ '?id]
        db teet-id)))

(defn- resolve-user-and-entity [db user entity-eid]
  [(->> user user-model/user-ref (du/entity db) :db/id)
   (:db/id (du/entity db entity-eid))])

(defn entity-seen-tx
  "Create a tx that records user has seen given entity."
  [db user entity-eid]
  (let [[user-id entity-id] (resolve-user-and-entity db user entity-eid)]
    {:db/id "entity-seen"
     :entity-seen/entity entity-eid
     :entity-seen/user user-id
     :entity-seen/entity+user [entity-id user-id]
     :entity-seen/seen-at (java.util.Date.)}))

(defn entity-seen
  "Pull the entity seen info."
  [db user entity-eid]
  (let [[user-id entity-id] (resolve-user-and-entity db user entity-eid)]
    (ffirst
     (d/q '[:find (pull ?seen [:entity-seen/seen-at])
            :in $ ?u ?e
            :where
            [?seen :entity-seen/entity ?e]
            [?seen :entity-seen/user ?u]]
          db user-id entity-id))))
