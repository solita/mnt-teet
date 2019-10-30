(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.user.user-roles :as user-roles]))

(defmethod db-api/command! :admin/create-user [{conn :conn} {:user/keys [id person-id roles]}]
  (d/transact conn
              {:tx-data [{:user/id id
                          :user/person-id person-id
                          :user/roles roles}]})
  :ok)

(defmethod db-api/command-authorization :admin/create-user [{user :user} _]
  (user-roles/require-role user :admin))
