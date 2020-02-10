(ns teet.project.project-db
  "Utilities for project datomic queries"
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api]))

(defn task-project-id [db task-id]
  (or (ffirst
       (d/q '[:find ?project
              :in $ ?t
              :where
              [?activity :activity/tasks ?t]
              [?lifecycle :thk.lifecycle/activities ?activity]
              [?project :thk.project/lifecycles ?lifecycle]]
            db task-id))
      (db-api/bad-request! "No such task")))

(defn activity-project-id [db activity-id]
  (or (ffirst
       (d/q '[:find ?project
              :in $ ?activity
              :where
              [?lifecycle :thk.lifecycle/activities ?activity]
              [?project :thk.project/lifecycles ?lifecycle]]
            db activity-id))
      (db-api/bad-request! "No such activity")))

(defn lifecycle-project-id [db lifecycle-id]
  (or (ffirst
       (d/q '[:find ?project
              :in $ ?lifecycle
              :where
              [?project :thk.project/lifecycles ?lifecycle]]
            db lifecycle-id))
      (db-api/bad-request! "No such lifecycle")))

(defn permission-project-id [db permission-id]
  ;; PENDING: currently permissions have one project
  (or (ffirst
       (d/q '[:find ?project
              :in $ ?permission
              :where [?permission :permission/projects ?project]]
            db permission-id))
      (db-api/bad-request! "No such permission")))
