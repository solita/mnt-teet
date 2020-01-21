(ns teet.project.project-queries
  (:require [teet.db-api.core :as db-api]
            [teet.project.project-model :as project-model]
            [datomic.client.api :as d]
            [clojure.string :as str]
            [teet.meta.meta-query :as meta-query]))

(defmethod db-api/query :thk.project/db-id->thk-id [{db :db} {id :db/id}]
  (-> db
      (d/pull [:thk.project/id] id)
      :thk.project/id))

(defmethod db-api/query :thk.project/fetch-project [{db :db} {:thk.project/keys [id]}]
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
        (update :thk.project/lifecycles project-model/sort-lifecycles)
        (update :thk.project/lifecycles
                (fn [lifecycle]
                  (map #(update % :thk.lifecycle/activities project-model/sort-activities) lifecycle))))))

(defmethod db-api/query :thk.project/fetch-task [{db :db} {project-id :thk.project/id
                                                           task-id :task-id}]
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

(defmethod db-api/query :thk.project/fetch-project-lifecycle
  [{db :db} {:keys [project lifecycle]}]
  (-> (d/q '[:find (pull ?e [*
                             {:thk.lifecycle/activities [:db/id
                                                         :activity/name
                                                         :activity/estimated-start-date
                                                         :activity/estimated-end-date]}
                             {:thk.project/_lifecycles [:db/id]}])
             :where [?project :thk.project/lifecycles ?e]
             :in $ ?project ?e]
           db
           [:thk.project/id project]
           lifecycle)
      ffirst
      (fetch-project db [:thk.project/_lifecycles 0])))

(defmethod db-api/query :thk.project/fetch-lifecycle-activity [{db :db} {:keys [lifecycle activity]}]
  (-> (d/q '[:find (pull ?e [:activity/name :activity/estimated-start-date :activity/estimated-end-date
                             {:activity/tasks [:task/status :task/name :task/assignee :task/description
                                               {:task/documents [*]}]}
                             {:thk.lifecycle/_activities
                              [:db/id
                               {:thk.project/_lifecycles
                                [:db/id]}]}])
             :where [?lifecycle :thk.lifecycle/activities ?e]
             :in $ ?lifecycle ?e]
           db
           lifecycle
           activity)
      ffirst
      (fetch-project db [:thk.lifecycle/_activities 0 :thk.project/_lifecycles 0])
      (fetch-lifecycle db [:thk.lifecycle/_activities 0])))

(defmethod db-api/query :thk.project/listing [{db :db} _]
  {:query '[:find (pull ?e columns)
            :in $ columns
            :where [?e :thk.project/id _]]
   :args [db project-model/project-listing-attributes]
   :result-fn (partial mapv first)})

(defmethod db-api/query :thk.project/search [{db :db} {:keys [text]}]
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
