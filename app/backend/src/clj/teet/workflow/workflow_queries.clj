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

(defmethod db-api/query :workflow/list-project-phases [{db :db} {:keys [thk-project-id]}]
  {:query '[:find (pull ?e [:db/id :phase/phase-name :phase/status
                            :phase/estimated-start-date :phase/estimated-end-date
                            {:phase/tasks [*]}])
            :in $ ?thk-project-id
            :where [?e :thk/id ?thk-project-id] [?e :phase/phase-name _]]
   :args [db thk-project-id]
   :result-fn (partial mapv first)})

(defmethod db-api/query :task/fetch-task [{db :db} {:keys [task-id]}]
  {:query '[:find (pull ?e [:db/id :task/description
                            {:task/type [:db/ident]}
                            {:task/assignee [:user/id]}
                            {:phase/_tasks [:db/id {:phase/phase-name [:db/ident]}]}
                            {:task/documents [*]}
                            #_{:task/comments [:comment/comment :comment/timestamp
                                             {:comment/author [:user/id]}]}])
            :in $ ?e]
   :args [db task-id]
   :result-fn ffirst})
