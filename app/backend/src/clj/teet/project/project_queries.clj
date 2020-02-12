(ns teet.project.project-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.project.project-model :as project-model]
            [teet.permission.permission-db :as permission-db]
            [datomic.client.api :as d]
            [clojure.string :as str]
            [teet.meta.meta-query :as meta-query]
            [teet.project.project-db :as project-db]))

(defquery :thk.project/db-id->thk-id
  {:doc "Fetch THK project id for given entity :db/id"
   :context {db :db}
   :args {id :db/id}
   :project-id nil
   :authorization {}}
  (-> db
      (d/pull [:thk.project/id] id)
      :thk.project/id))

(defquery :thk.project/fetch-project
  {:doc "Fetch project information"
   :context {db :db}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :authorization {:project/project-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}}
  (let [project (meta-query/without-deleted
                  db
                  (d/pull db (into project-model/project-info-attributes
                                   '[{:thk.project/lifecycles
                                      [:db/id
                                       :thk.lifecycle/estimated-start-date
                                       :thk.lifecycle/estimated-end-date
                                       :thk.lifecycle/type
                                       {:thk.lifecycle/activities
                                        [*
                                         {:activity/tasks [*
                                                           {:task/documents [{:document/files [*]}]}]}]}]}])
                          [:thk.project/id id]))]
    (-> project
        (assoc :thk.project/permitted-users (project-model/users-with-permission
                                              (permission-db/valid-project-permissions db (:db/id project))))
        (update :thk.project/lifecycles project-model/sort-lifecycles)
        (update :thk.project/lifecycles
                (fn [lifecycle]
                  (map #(update % :thk.lifecycle/activities project-model/sort-activities) lifecycle))))))

(defquery :thk.project/fetch-task
  {:doc "Fetch task"
   :context {db :db}
   :args {project-id :thk.project/id
          task-id    :task-id}
   :pre [(project-db/task-belongs-to-project db [:thk.project/id project-id] task-id)]
   :project-id (project-db/task-project-id db task-id)
   :authorization {:task/task-information {:db/id task-id
                                           :link :task/assignee}}}
  (let [result (meta-query/without-deleted
                 db
                 (ffirst
                   (d/q '[:find
                          (pull ?e [:db/id
                                    :task/description
                                    :task/modified
                                    {:task/status [:db/ident]}
                                    {:task/type [:db/ident]}
                                    {:task/assignee [:user/id :user/given-name :user/family-name :user/email]}
                                    {:activity/_tasks [:db/id
                                                       {:activity/name [:db/ident]}
                                                       {:thk.lifecycle/_activities [:db/id]}]}
                                    {:task/documents [*
                                                      {:document/author [:user/id :user/given-name :user/family-name :user/email]}
                                                      {:document/files
                                                       [*
                                                        {:file/comments [*
                                                                         {:comment/author [*]}]}]}
                                                      {:document/comments [*
                                                                           {:comment/author [*]}]}]}])
                          :in $ ?e ?project-id
                          :where
                          [?p :thk.project/id ?project-id]
                          [?p :thk.project/lifecycles ?l]
                          [?l :thk.lifecycle/activities ?a]
                          [?a :activity/tasks ?e]]
                        db task-id project-id)))]
    (assoc result :project (d/pull db
                                   project-model/project-info-attributes
                                   [:thk.project/id project-id]))))


(defn- fetch-project [result db path]
  (let [project (d/pull db
                        project-model/project-info-attributes
                        (get-in result (into path [:db/id])))]
    (assoc result :project project)))

(defn- fetch-lifecycle [result db path]
  (let [project (d/pull db
                        [:thk.lifecycle/type]
                        (get-in result (into path [:db/id])))]
    (assoc result :lifecycle project)))

(defquery :thk.project/listing
  {:doc "List all project basic info"
   :context {db :db}
   :args _
   :project-id nil
   :authorization {}}
  (mapv first
        (d/q '[:find (pull ?e columns)
               :in $ columns
               :where [?e :thk.project/id _]]
             db project-model/project-listing-attributes)))

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
