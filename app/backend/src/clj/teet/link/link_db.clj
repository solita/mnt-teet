(ns teet.link.link-db
  "Common db queries for fetching links between entities"
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]
            [teet.link.link-model :as link-model]
            [teet.log :as log]))

(defmulti link-from
  "Check permissions and preconditions for linking from an entity.

  Returns falsy value if linking is not allowed.
  May also throw exception with error code for frontend.

  If return value is a map and contains :wrap-tx function the
  link data will be passed through the function before being
  transacted.

  Default behaviour is to disallow."
  (fn [_db _user [from-type _from-id] type _to] [from-type type]))

(defmulti delete-link-from
  "Check permissions and preconditions for deleting an existing link.

  Returns falsy value if linking is not allowed.
  May also throw exception with error code for frontend.

  If return value is a map and contains :wrap-tx function the
  link data will be passed through the function before being
  transacted.

  Default behaviour is to disallow."
  (fn [_db _user [from-type _from-id] type _to] [from-type type]))

(defmethod link-from :default [_ user from type to]
  (log/warn "Disallow link by user" user "from" from "to" to "(" type ")")
  false)

(defmethod delete-link-from :default [_ user from type to]
  (log/warn "Disallow link delete by user" user "from" from "to" to "(" type ")")
  false)

(defmulti fetch-external-link-info (fn [_user type _external-id]
                                     type))

(defmulti fetch-link-info (fn [_db _user type _link-to] type))

(defmethod fetch-link-info :default
  [db _ type link-to]
  (d/pull db (into link-model/common-link-target-attributes
                   (get-in link-model/link-types
                           [type :display-attributes]))
          link-to))

(defn expand-links
  "Expand all links in the given form to their display representations.
  Each link is added with a :link/info which is the fetched display
  representation."
  [db user valid-external-ids-by-type form]
  (walk/prewalk
   (fn [x]
     (if (and (map? x)
              (contains? x :link/type))
       (merge x
              {:link/info

               (cond
                 (:link/external-id x)
                 (let [valid-ids (or (valid-external-ids-by-type (:link/type x))
                                     (constantly true))]
                   (assoc (fetch-external-link-info user (:link/type x) (:link/external-id x))
                     :link/valid?
                     (boolean (valid-ids (:link/external-id x)))))

                 (:link/to x)
                 (fetch-link-info db user (:link/type x) (:db/id (:link/to x))))})
       x))
   form))

(defn- link-info-for-entity
  [db entity-id return-links-to-deleted?]
  (mapv first (d/q (into '[:find (pull ?l [:db/id :link/to :link/external-id :link/type
                                           :meta/created-at])
                           :in $ ?e
                           :where
                           [?l :link/from ?e]]
                         (when-not return-links-to-deleted?
                           '[(or-join [?l]
                                      [?l :link/external-id _]
                                      (and [?l :link/to ?target]
                                           [(missing? $ ?target :meta/deleted?)]))]))
                   db entity-id)))

(defn fetch-links
  "Fetch links for all entities in form that match fetch-links-pred?.
  Associates :link/_from with list of linked entities and expands the
  linked to entities."
  ([db user valid-external-ids-by-type fetch-links-pred? form]
   (fetch-links {:db db
                 :user user
                 :valid-external-ids-by-type valid-external-ids-by-type
                 :fetch-links-pred? fetch-links-pred?}
                form))
  ([{:keys [db user valid-external-ids-by-type fetch-links-pred? return-links-to-deleted?]
     :or {valid-external-ids-by-type (constantly #{})
          return-links-to-deleted? true}}
    form]
   (walk/prewalk
     (fn [x]
       (if (and (map? x)
                (contains? x :db/id)
                (fetch-links-pred? x))
         (assoc x :link/_from
                  (expand-links
                    db user
                    valid-external-ids-by-type
                    (sort-by :meta/created-at
                             (link-info-for-entity db (:db/id x) return-links-to-deleted?))))
         x))
     form)))

(defn is-link?
  "Check that the given link has the type and from/to entities."
  [db link-id [_from-type from-id] to type]
  (let [link (d/pull db [:link/from :link/to :link/type] link-id)]
    (and (= from-id (get-in link [:link/from :db/id]))
         (= (:db/id to) (get-in link [:link/to :db/id]))
         (= type (:link/type link)))))
