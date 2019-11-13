(ns teet.project.project-queries
  (:require [teet.db-api.core :as db-api]
            [teet.project.project-model :as project-model]))


(defmethod db-api/query :thk.project/listing [{db :db} args]
  {:query '[:find (pull ?e columns)
            :in $ columns
            :where [?e :thk.project/id _]]
   :args [db project-model/project-listing-columns]
   :result-fn (partial mapv first)})
