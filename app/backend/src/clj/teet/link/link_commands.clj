(ns teet.link.link-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [teet.link.link-model :as link-model]
            [teet.link.link-db :as link-db]
            [teet.util.datomic :as du]))

(defcommand :link/add-link
  {:doc "Add link from one entity to another"
   :context {:keys [db user]}
   :payload {:keys [from to type]}
   :project-id nil
   :authorization {}
   :pre [(link-model/valid-from? from)]}

  (when-let [{wrap-tx :wrap-tx} (link-db/link-from db user from type to)]
    (select-keys
     (tx ((or wrap-tx identity)
          [{:db/id "add-link"
            :link/from (nth from 1)
            :link/to to
            :link/type type}]))
     [:tempids])))

(defcommand :link/delete
  {:doc "Delete link by id"
   :context {:keys [db user]}
   :payload {:keys [from to type]
             id :db/id}
   :pre [(link-db/is-link? db id from to type)]
   :project-id nil
   :authorization {}}
  (when-let [{wrap-tx :wrap-tx} (link-db/delete-link-from db user from type to)]
    (tx ((or wrap-tx identity)
         [[:db/retractEntity id]]))
    :ok))
