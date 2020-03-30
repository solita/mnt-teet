(ns teet.project.project-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.project.project-model :as project-model]
            [teet.permission.permission-db :as permission-db]
            [datomic.client.api :as d]
            [clojure.string :as str]
            [teet.meta.meta-query :as meta-query]
            [teet.project.project-db :as project-db]
            [teet.util.collection :as cu]
            [teet.task.task-db :as task-db]))

(defquery :thk.project/db-id->thk-id
  {:doc "Fetch THK project id for given entity :db/id"
   :context {db :db}
   :args {id :db/id}
   :project-id nil
   :authorization {}}
  (-> db
      (d/pull [:thk.project/id] id)
      :thk.project/id))

(defn maybe-fetch-task-files [project db task-id]
  (if-not task-id
    project
    (cu/update-path
     project
     [:thk.project/lifecycles some? ; all lifecycles
      :thk.lifecycle/activities some? ; all activities
      :activity/tasks #(= task-id (:db/id %))] ; matching task

     ;; Fetch and assoc the tasks
     assoc :task/files (task-db/files-for-task db task-id))))

(defquery :thk.project/fetch-project
  {:doc "Fetch project information"
   :context {db :db}
   :args {:thk.project/keys [id]
          task-id :task-id}
   :project-id [:thk.project/id id]
   :authorization {:project/project-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}}
  (let [project (meta-query/without-deleted
                  db
                  (project-db/project-by-id db [:thk.project/id id]))]
    (-> project
        (assoc :thk.project/permitted-users
               (project-model/users-with-permission
                (permission-db/valid-project-permissions db (:db/id project))))
        (update :thk.project/lifecycles project-model/sort-lifecycles)
        (update :thk.project/lifecycles
                (fn [lifecycle]
                  (map #(update % :thk.lifecycle/activities project-model/sort-activities) lifecycle)))
        (maybe-fetch-task-files db task-id))))


(defquery :thk.project/listing
  {:doc "List all project basic info"
   :context {db :db}
   :args _
   :project-id nil
   :authorization {}}
  (map
    project-model/project-with-status
    (meta-query/without-deleted
      db
      (mapv first
            (d/q '[:find (pull ?e columns)
                   :in $ columns
                   :where [?e :thk.project/id _]]
                 db project-model/project-list-with-status-attributes)))))

(defquery :thk.project/search
  {:doc "Search for a project by text"
   :context {db :db}
   :args {:keys [text]}
   :project-id nil
   :authorization {}}
  {:query '[:find (pull ?e [:db/id :thk.project/project-name :thk.project/name :thk.project/id])
            :where
            (or [?e :thk.project/project-name ?name]
                [?e :thk.project/name ?name])
            [(.toLowerCase ^String ?name) ?lower-name]
            [(.contains ?lower-name ?text)]
            :in $ ?text]
   :args [db (str/lower-case text)]
   :result-fn (partial mapv
                       #(-> % first (assoc :type :project)))})
