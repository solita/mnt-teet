(ns teet.link.link-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.link.link-model :as link-model]
            [teet.link.link-db :as link-db]
            [teet.util.datomic :as du]))

(defcommand :link/add-link
  {:doc "Add link from one entity to another"
   :context {:keys [db user]}
   :payload {:keys [from to type]}
   :project-id nil
   :authorization {}
   :pre [(link-model/valid-from? from)
         (link-db/allow-link? db user from type to)]
   :transact
   [{:db/id "add-link"
     :link/from (nth from 1)
     :link/to to
     :link/type type}]})

(defcommand :link/delete
  {:doc "Delete link by id"
   :context {:keys [db user]}
   :payload {:keys [from to type]
             id :db/id}
   :pre [(link-db/is-link? db id from to type)
         (link-db/allow-link-delete? db user from type to)]
   :project-id nil
   :authorization {}
   :transact [[:db/retractEntity id]]})
