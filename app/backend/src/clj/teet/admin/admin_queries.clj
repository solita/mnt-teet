(ns teet.admin.admin-queries
  (:require [teet.db-api.core :as db-api]
            [teet.user.user-roles :as user-roles]))

(defmethod db-api/query :admin/list-users [{db :db} _]
  {:query '[:find (pull ?e [*])
            :where [?e :user/id _]]
   :args [db]
   :result-fn (partial mapv first)})

(defmethod db-api/query-authorization :admin/list-users [{user :user} _]
  (user-roles/require-role user :admin))
