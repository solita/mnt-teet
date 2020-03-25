(ns teet.project.project-db
  "Utilities for project datomic queries"
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api]
            [teet.project.project-model :as project-model]))

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

(defn task-belongs-to-project [db project-id task-id]
  (ffirst
   (d/q '[:find ?project
          :in $ ?t ?project
          :where
          [?activity :activity/tasks ?t]
          [?lifecycle :thk.lifecycle/activities ?activity]
          [?project :thk.project/lifecycles ?lifecycle]]
        db task-id project-id)))

(defn file-project-id [db file-id]
  (or (ffirst
       (d/q '[:find ?project
              :in $ ?f
              :where
              [?task :task/files ?f]
              [?activity :activity/tasks ?task]
              [?lifecycle :thk.lifecycle/activities ?activity]
              [?project :thk.project/lifecycles ?lifecycle]]
            db file-id))
      (db-api/bad-request! "No such file")))

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

(defn document-project-id
  ([db document-id]
   (document-project-id db document-id ::throw))
  ([db document-id default-value]
   (or
    (ffirst
     (d/q '[:find ?project
            :in $ ?doc
            :where
            [?task :task/documents ?doc]
            [?activity :activity/tasks ?task]
            [?lifecycle :thk.lifecycle/activities ?activity]
            [?project :thk.project/lifecycles ?lifecycle]]
          db document-id))
    (if (= default-value ::throw)
      (db-api/bad-request! "No such document")
      default-value))))

(defn project-by-id
  "Fetch project by id. Includes all information on nested items required by project navigator."
  [db eid]
  (d/pull db (into project-model/project-info-attributes
                   '[{:thk.project/lifecycles
                      [:db/id
                       :thk.lifecycle/estimated-start-date
                       :thk.lifecycle/estimated-end-date
                       :thk.lifecycle/type
                       {:thk.lifecycle/activities
                        [*
                         {:activity/tasks [*
                                           {:task/files [*
                                                         {:file/author [:user/id :user/family-name :user/given-name]}]}
                                           {:task/assignee [*]}]}]}]}])
          eid))

(defn entity-project-id [db entity-type entity-id]
  (case entity-type
    :activity (activity-project-id db entity-id)
    :task (task-project-id db entity-id)
    :file (file-project-id db entity-id)))
