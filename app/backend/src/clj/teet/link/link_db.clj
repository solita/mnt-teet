(ns teet.link.link-db
  "Common db queries for fetching links between entities"
  (:require [clojure.walk :as walk]
            [datomic.client.api :as d]
            [teet.link.link-model :as link-model]
            [teet.log :as log]))

(defmulti allow-link?
  "Check permissions for linking. Returns true if linking is allowed or
  false if not. May also throw exception with error code for frontend.

  Dispatches on from type and link type.
  Default behaviour is to disallow."
  (fn [_db _user [from-type _from-id] type _to] [from-type type]))

(defmulti allow-link-delete?
  "Check permissions for deleting an existing link. Returns true if
  deleting the link is allowed or false if not. May also throw an
  exception with error code for the frontend.

  Dispatches on from type and link type.
  Default behaviour is to disallow."
  (fn [_db _user [from-type _from-id] type _to] [from-type type]))

(defmethod allow-link? :default [_ user from type to]
  (log/warn "Disallow link by user" user "from" from "to" to "(" type ")")
  false)

(defmethod allow-link-delete? :default [_ user from type to]
  (log/warn "Disallow link delete by user" user "from" from "to" to "(" type ")")
  false)


(defn expand-links
  "Expand all links in the given form to their display representations.
  Each link is added with a :link/info which is the fetched display
  representation."
  [db form]
  (walk/prewalk
   (fn [x]
     (if (and (map? x)
              (contains? x :link/type)
              (contains? x :link/to))
       (merge x
              {:link/info (d/pull db (get-in link-model/link-types
                                             [(:link/type x) :display-attributes])
                                  (:db/id (:link/to x)))})
       x))
   form))

(defn is-link?
  "Check that the given link has the type and from/to entities."
  [db link-id [_from-type from-id] to type]
  (let [link (d/pull db [:link/from :link/to :link/type] link-id)]
    (and (= from-id (get-in link [:link/from :db/id]))
         (= (:db/id to) (get-in link [:link/to :db/id]))
         (= type (:link/type link)))))
