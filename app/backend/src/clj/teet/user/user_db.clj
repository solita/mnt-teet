(ns teet.user.user-db
  (:require [datomic.client.api :as d]
            [teet.permission.permission-db :as permission-db]
            [teet.user.user-model :as user-model]))

(defn user-roles
  "Given a datomic connection and a user uuid, return a set of user's roles."
  [db user-ref]
  (-> db
      (d/pull '[:user/roles] (user-model/user-ref user-ref))
      :user/roles
      set))

(defn user-info
  "Fetch user information with current valid permissions."
  [db user-ref]
  (let [{id :db/id :as user} (d/pull db '[:user/id :user/given-name :user/family-name :user/email
                                          :user/person-id :db/id]
                                     (user-model/user-ref user-ref))]
    (assoc user :user/permissions
           (when id
             (permission-db/user-permissions db id)))))
