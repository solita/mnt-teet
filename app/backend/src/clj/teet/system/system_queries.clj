(ns teet.system.system-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.util.build-info :as build-info]))

(defn db-state-response [_db-result]
  (with-meta
    {:status 200
     :body (build-info/git-commit)}
    {:format :raw}))

;; The actual query here is irrelevant. This provides an endpoint for
;; checking whether the db is alive.
(defquery :teet.system/db
  {:doc "Check database status"
   :context {db :db}
   :args _
   :unauthenticated? true}
  {:query '[:find ?e
            :where
            [?e :thk.project/project-name "non-existent"]]
   :args [db]
   :result-fn db-state-response})
