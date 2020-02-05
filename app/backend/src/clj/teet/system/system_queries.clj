(ns teet.system.system-queries
  (:require [teet.db-api.core :as db-api]))

(defn db-state-response [_db-result]
  (str (System/currentTimeMillis)))

;; Allow anonymous requests
(defmethod db-api/query-authorization :teet.system/db [_ _]
  nil)

;; The actual query here is irrelevant. This provides an endpoint for
;; checking whether the db is alive.
(defmethod db-api/query :teet.system/db [{db :db} _]
  {:query     '[:find ?e
                :where
                [?e :thk.project/project-name "non-existent"]]
   :args      [db]
   :result-fn db-state-response})
