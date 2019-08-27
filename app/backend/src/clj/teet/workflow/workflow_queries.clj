(ns teet.workflow.workflow-queries
  (:require [teet.db-api.core :as db-api]))

(defmethod db-api/query :fetch-workflow [db {:keys [id]}]
  {:query '[:find (pull ?e [:workflow/name
                            {:workflow/phases
                             [:db/id
                              :phase/name
                              {:phase/tasks [:db/id :task/status]}]}])
            :in $ ?id
            :where [?e :db/id ?id]]
   :args [db id]})
