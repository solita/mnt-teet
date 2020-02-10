(ns teet.user.user-db
  (:require [datomic.client.api :as d]
            [teet.permission.permission-db :as permission-db]))

(defn user-roles
  "Given a datomic connection and a user uuid, return a set of user's roles."
  [db id]
  (-> db
      (d/pull '[:user/roles] [:user/id id])
      :user/roles
      set))

(defn user-info
  "Fetch user information with current valid permissions."
  [db id]
  (let [{id :db/id :as user} (d/pull db '[:user/id :user/given-name :user/family-name :user/email
                                          :user/person-id :db/id]
                                     [:user/id id])]
    (assoc user :user/permissions
           (when id
             (permission-db/user-permissions db id)))))
