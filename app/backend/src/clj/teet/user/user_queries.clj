(ns teet.user.user-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]))

(defquery :user/list
  {:doc "List all users"
   :context {db :db}
   :args _

   ;; FIXME: can anyone list users?
   :project-id nil
   :authorization {}}
  {:query '[:find (pull ?e [:user/id :user/given-name :user/family-name :user/email])
            :where [?e :user/id _]]
   :args [db]
   :result-fn (partial mapv first)})
