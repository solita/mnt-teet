(ns teet.authorization.authorization-queries
  "Fetch enumeration values"
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.authorization.authorization-core :as authorization]))


(defquery :authorization/permissions
  {:doc "Fetch required permissions for commands and queries"
   :args {}
   :spec empty?
   :unauthenticated? true}
  @db-api/request-permissions)


(defquery :authorization/user-is-permitted?
  {:doc "Query to check if the user is allowed to do the given action in the given context"
   :args {action :action
          :as args}
   :context {:keys [user db]}
   :allowed-for-all-users? true}
  (let [authorization-for-action (:contract-authorization-rule (action @db-api/request-permissions))]
    (authorization/authorized-for-action? (merge {:user user :db db :action authorization-for-action}
                                                 (select-keys args [:company
                                                                    :target
                                                                    :contract
                                                                    :entity-id])))))
