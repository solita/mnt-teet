(ns teet.user.user-queries
  (:require [teet.db-api.core :as db-api]))

(defmethod db-api/query :user/list [{db :db} _]
  {:query '[:find (pull ?e [:user/id :user/given-name :user/family-name :user/email])
            :where [?e :user/id _]]
   :args [db]
   :result-fn first})
