(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.user.user-roles :as user-roles]
            [taoensso.timbre :as log]))

(defn- new-user []
  {:user/id (java.util.UUID/randomUUID)
   :user/roles [:user]})

(defmethod db-api/command! :admin/create-user [{conn :conn} user-data]
  (let [user (merge (new-user)
                    (select-keys user-data [:user/id :user/person-id :user/roles]))]
    (log/info "CREATE USER: " user)
    (d/transact conn
                {:tx-data [user]})
    :ok))

(defmethod db-api/command-authorization :admin/create-user [{user :user} _]
  (user-roles/require-role user :admin))
