(ns teet.migration.enum-values
  (:require [datomic.client.api :as d]))

(defn remove-old-task-types [conn]
  (let [types (d/q '[:find (pull ?e [*])
                     :where [?e :enum/attribute :task/type]]
                   (d/db conn))]
    (d/transact
     conn
     {:tx-data (for [[{id :db/id}] types]
                 [:db/retractEntity id])})))
