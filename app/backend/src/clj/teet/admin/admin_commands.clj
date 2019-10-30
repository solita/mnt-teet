(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]))

(defmethod db-api/command! :admin/create-user [{conn :conn} {:user/keys [id person-id roles]}]
  (d/transact conn
              {:tx-data [{:user/id id
                          :user/person-id person-id
                          :user/roles roles}]})
  :ok)
