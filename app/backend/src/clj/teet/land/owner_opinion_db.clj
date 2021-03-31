(ns teet.land.owner-opinion-db
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api]))

(defn owner-opinions [db project-eid land-unit-id]
  (mapv
    first
    (d/q {:query {:find '[(pull ?opinion [* {:land-owner-opinion/activity [:activity/name :db/id]}
                                          {:meta/modifier [:user/given-name :user/family-name]}
                                          {:meta/creator [:user/given-name :user/family-name]}])]
                     :where
                     '[[?opinion :land-owner-opinion/project ?project]
                       [?opinion :land-owner-opinion/land-unit ?land-unit-id]
                       [(missing? $ ?opinion :meta/deleted?)]]
                     :in '[$ ?project ?land-unit-id]}
             :args [db project-eid land-unit-id]})))

(defn get-project-id [db opinion-id]
  (let [project-id (ffirst
                     (d/q '[:find ?project
                            :in $ ?o
                            :where
                            [?o :land-owner-opinion/activity ?activity]
                            [?lifecycle :thk.lifecycle/activities ?activity]
                            [?project :thk.project/lifecycles ?lifecycle]]
                       db opinion-id))]
    (or project-id
      (db-api/bad-request! "No such opinion"))))
