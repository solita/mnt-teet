(ns teet.project.project-queries
  (:require [teet.db-api.core :as db-api]))


(defmethod db-api/query :thk.project/listing [{db :db} args]
  {:query '[:find (pull ?e [:thk.project/name :thk.project/id])
            :where [?e :thk.project/id _]]
   :args [db]
   :result-fn (partial mapv first)})
