(ns teet.link.link-db
  "Common db queries for fetching links between entities"
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]
            [teet.link.link-model :as link-model]
            [teet.log :as log]
            [teet.integration.postgrest :as postgrest]
            [teet.environment :as environment]))

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


(defn expand-links
  "Expand all links in the given form to their display representations.
  Each link is added with a :link/info which is the fetched display
  representation."
  [db user form]
  (walk/prewalk
   (fn [x]
     (if (and (map? x)
              (contains? x :link/type))
       (merge x
              {:link/info

               (cond
                 (:link/external-id x)
                 (fetch-external-link-info user (:link/type x) (:link/external-id x))

                 (:link/to x)
                 (d/pull db (into link-model/common-link-target-attributes
                                  (get-in link-model/link-types
                                          [(:link/type x) :display-attributes]))
                         (:db/id (:link/to x))))})
       x))
   form))

(defn fetch-links
  "Fetch links for all entities in form that match fetch-links-pred?.
  Associates :link/_from with list of linked entities and expands the
  linked to entities."
  [db user fetch-links-pred? form]
  (walk/prewalk
   (fn [x]
     (if (and (map? x)
              (contains? x :db/id)
              (fetch-links-pred? x))
       (assoc x :link/_from
              (expand-links
               db user
               (sort-by :meta/created-at
                        (mapv first
                              (d/q '[:find (pull ?l [:db/id :link/to :link/external-id :link/type
                                                     :meta/created-at])
                                     :where [?l :link/from ?e]
                                     :in $ ?e]
                                   db (:db/id x))))))
       x))
   form))

(defn is-link?
  "Check that the given link has the type and from/to entities."
  [db link-id [_from-type from-id] to type]
  (let [link (d/pull db [:link/from :link/to :link/type] link-id)]
    (and (= from-id (get-in link [:link/from :db/id]))
         (= (:db/id to) (get-in link [:link/to :db/id]))
         (= type (:link/type link)))))
