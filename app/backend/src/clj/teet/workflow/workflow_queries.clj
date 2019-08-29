(ns teet.workflow.workflow-queries
  (:require [teet.db-api.core :as db-api]))

(defmethod db-api/query :fetch-workflow [{db :db} {:keys [id]}]
  {:query '[:find (pull ?e [:workflow/name
                            {:workflow/phases
                             [:db/id
                              :phase/name
                              {:phase/tasks [:db/id :task/status]}]}])
            :in $ ?id
            :where [?e :db/id ?id]]
   :args [db id]})

(defmethod db-api/query :workflow/list-project-workflows [{db :db} {:keys [thk-project-id]}]
  {:query '[:find (pull ?e [:workflow/name :workflow/due-date])
            :in $ ?thk-project-id
            :where [?e :thk/id ?thk-project-id]]
   :args [db thk-project-id]})
