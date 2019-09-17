(ns teet.workflow.workflow-queries
  (:require [teet.db-api.core :as db-api]))

(defmethod db-api/query :workflow/fetch-workflow [{db :db} {:keys [project-id workflow-id]}]
  {:query '[:find (pull ?e [:db/id :workflow/name
                            {:workflow/phases
                             [:db/id
                              :phase/name
                              :phase/due-date
                              {:phase/tasks [:db/id :task/name
                                             {:task/status [:task.status/timestamp
                                                            :task.status/status]}]}]}])
            :in $ ?e ?project-id
            :where [?e :thk/id ?project-id]]
   :args [db workflow-id project-id]
   :result-fn ffirst})

(defmethod db-api/query :workflow/list-project-workflows [{db :db} {:keys [thk-project-id]}]
  {:query '[:find (pull ?e [:db/id :workflow/name :workflow/due-date])
            :in $ ?thk-project-id
            :where [?e :thk/id ?thk-project-id] [?e :workflow/name _]]
   :args [db thk-project-id]
   :result-fn (partial mapv first)})

(defmethod db-api/query :task/fetch-task [{db :db} {:keys [task-id]}]
  {:query '[:find (pull ?e [:db/id :task/name
                            {:phase/_tasks [:db/id :phase/name
                                            {:workflow/_phases [:db/id :workflow/name :thk/id]}]}
                            {:task/status [*]}
                            {:task/documents [*]}
                            {:task/comments [:comment/comment :comment/timestamp
                                             {:comment/author [:user/id]}]}])
            :in $ ?e]
   :args [db task-id]
   :result-fn ffirst})
