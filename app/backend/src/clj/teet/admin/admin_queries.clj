(ns teet.admin.admin-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.user.user-roles :as user-roles]))

(defquery :admin/list-users
  {:doc "List users who have been given access"
   :context {db :db user :user}
   :pre [(user-roles/require-role user :admin)]
   :args _
   :project-id nil
   :authorization {:admin/add-user {}}}
  {:query '[:find (pull ?e [*])
            :where [?e :user/id _]]
   :args [db]
   :result-fn (partial mapv first)})
