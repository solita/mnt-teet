(ns teet.workflow.workflow-queries
  (:require [teet.db-api.core :as db-api]))

(defmethod db-api/query :workflow/fetch-workflow [{db :db} {:keys [project-id workflow-id]}]
  {:query '[:find (pull ?e [:db/id :workflow/name
                            {:workflow/activities
                             [:db/id
                              :activity/name
                              :activity/due-date
                              {:activity/tasks [:db/id :task/name
                                             {:task/status [:task.status/timestamp
                                                            :task.status/status]}]}]}])
            :in $ ?e ?project-id
            :where [?e :thk/id ?project-id]]
   :args [db workflow-id project-id]
   :result-fn ffirst})

(defmethod db-api/query :workflow/project [{db :db} {:keys [thk-project-id]}] ;; TODO: when thk in datomic fetch that also
  {:query '[:find (pull ?e [:db/id :activity/activity-name :activity/status
                            :activity/estimated-start-date :activity/estimated-end-date
                            {:activity/tasks [*]}])
            :in $ ?thk-project-id
            :where [?e :thk/id ?thk-project-id] [?e :activity/activity-name _]]
   :args [db thk-project-id]
   :result-fn #(hash-map :thk-id thk-project-id :activities (mapv first %))})

(defmethod db-api/query :task/fetch-task [{db :db} {:keys [task-id]}]
  {:query '[:find (pull ?e [:db/id
                            :task/description
                            :task/modified
                            :activity/status
                            {:task/status [:db/ident]}
                            {:task/type [:db/ident]}
                            {:task/assignee [:user/id :user/given-name :user/family-name :user/email]}
                            {:activity/_tasks [:db/id {:activity/activity-name [:db/ident]}]}
                            {:task/documents [*]}])
            :in $ ?e]
   :args [db task-id]
   :result-fn ffirst})
