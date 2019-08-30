(ns teet.workflow.workflow-queries
  (:require [teet.db-api.core :as db-api]))

(defmethod db-api/query :workflow/fetch-workflow [{db :db} {:keys [project-id workflow-id]}]
  {:query '[:find (pull ?e [:workflow/name
                            {:workflow/phases
                             [:db/id
                              :phase/name
                              {:phase/tasks [:db/id :task/status]}]}])
            :in $ ?e ?project-id
            :where [?e :thk/id ?project-id]]
   :args [db workflow-id project-id]
   :result-fn ffirst})

(defmethod db-api/query :workflow/list-project-workflows [{db :db} {:keys [thk-project-id]}]
  {:query '[:find (pull ?e [:workflow/name :workflow/due-date])
            :in $ ?thk-project-id
            :where [?e :thk/id ?thk-project-id]]
   :args [db thk-project-id]})
