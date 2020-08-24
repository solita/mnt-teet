(ns teet.project.project-db
  "Utilities for project datomic queries"
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api]
            [teet.project.project-model :as project-model]
            [teet.util.datomic :as du]
            [teet.comment.comment-model :as comment-model]))

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

(defn estate-comments-project-id [db ec-id]
  (or (-> (d/pull db '[:estate-comments/project] ec-id)
          :estate-comments/project
          :db/id)
      (db-api/bad-request! "No such estate-comments entity")))

(defn owner-comments-project-id [db oc-id]
  (or (-> (d/pull db '[:owner-comments/project] oc-id)
          :owner-comments/project
          :db/id)
      (db-api/bad-request! "No such owner-comments entity")))

(defn unit-comments-project-id [db oc-id]
  (or (-> (d/pull db '[:unit-comments/project] oc-id)
          :unit-comments/project
          :db/id)
      (db-api/bad-request! "No such unit-comments entity")))

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
                         {:activity/manager [:user/given-name :user/family-name :db/id]}
                         {:activity/tasks [:db/id
                                           :task/name :task/description
                                           :task/status :task/type :task/group
                                           :task/estimated-start-date :task/estimated-end-date
                                           :task/actual-start-date :task/actual-end-date
                                           :task/send-to-thk?
                                           {:task/assignee [:user/given-name
                                                            :user/email
                                                            :user/id
                                                            :db/id
                                                            :user/family-name]}]}]}]}])
          eid))

(defn lifecycle-dates
  [db lifecycle-id]
  (d/pull db
          '[:thk.lifecycle/estimated-start-date :thk.lifecycle/estimated-end-date]
          lifecycle-id))

(defn entity-project-id [db entity-type entity-id]
  (case entity-type
    :activity (activity-project-id db entity-id)
    :task (task-project-id db entity-id)
    :file (file-project-id db entity-id)
    :owner-comments (first (second entity-id))
    :estate-comments (first (second entity-id))
    :unit-comments (first (second entity-id))))

(defn comment-project-id [db comment-id]
  (let [ce (du/entity db comment-id)]
    (some (fn [path]
            (get-in ce path))
          comment-model/comment-project-paths)))

(defn project-owner [db project-eid]
  (get-in (d/pull db '[:thk.project/owner] project-eid)
          [:thk.project/owner :db/id]))

(defn project-exists?
  "Check if TEET has a project with the given THK project id"
  [db thk-project-id]
  (boolean
   (ffirst (d/q '[:find ?e :where [?e :thk.project/id ?project-id] :in $ ?project-id]
                db thk-project-id))))

(defn project-has-owner?
  [db project-eid]
  (boolean
   (ffirst (d/q '[:find ?e :where [?project :thk.project/owner ?e] :in $ ?project]
                db project-eid))))


