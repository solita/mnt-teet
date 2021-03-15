(ns teet.land.owner-opinion-db
  (:require [datomic.client.api :as d]))

(defn owner-opinions [db project-eid land-unit-id]
  (mapv
    first
    (d/q {:query {:find '[(pull ?opinion [*])]
                     :where
                     '[[?opinion :land-owner-opinion/project ?project]
                       [?opinion :land-owner-opinion/land-unit ?land-unit-id]]
                     :in '[$ ?project ?land-unit-id]}
             :args [db project-eid land-unit-id]})))
