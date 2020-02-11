(ns teet.system.system-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]))

(defn db-state-response [_db-result]
  (str (System/currentTimeMillis)))

;; Allow anonymous requests
(defmethod db-api/query-authorization :teet.system/db [_ _]
  nil)

;; The actual query here is irrelevant. This provides an endpoint for
;; checking whether the db is alive.
(defquery :teet.system/db
  {:doc "Check database status"
   :context {db :db}
   :args _
   :project-id nil
   :authorization {}}
  {:query '[:find ?e
            :where
            [?e :thk.project/project-name "non-existent"]]
   :args [db]
   :result-fn db-state-response})
