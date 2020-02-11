(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            [teet.user.user-roles :as user-roles]
            [teet.log :as log]
            teet.user.user-spec))

(defn- new-user []
  {:user/id (java.util.UUID/randomUUID)
   :user/roles [:user]})

(defcommand :admin/create-user
  {:doc "Create user"
   :context {:keys [conn user]}
   :payload user-data
   :pre [(user-roles/require-role user :admin)]
   :project-id nil
   :authorization {:admin/add-user {}}
   :transact [(merge (new-user)
                     (select-keys user-data [:user/id :user/person-id :user/roles]))]})
