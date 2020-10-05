(ns teet.link.link-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.link.link-model :as link-model]
            [teet.link.link-db :as link-db]))

(defcommand :link/add-link
  {:doc "Add link from one entity to another"
   :context {:keys [db user]}
   :payload {:keys [from to type]}
   :project-id nil
   :authorization {}
   :pre [(link-model/valid-from? from)
         (link-db/allow-link? db user from type to)]
   :transact [{:db/id "add-link"
               :link/from (nth from 1)
               :link/to to
               :link/type type}]})
