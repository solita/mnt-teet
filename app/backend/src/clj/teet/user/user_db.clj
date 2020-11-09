(ns teet.user.user-db
  (:require [datomic.client.api :as d]
            [teet.permission.permission-db :as permission-db]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]))

(defn user-info
  "Fetch user information with current valid permissions."
  [db user-ref]
  (let [{id :db/id :as user} (d/pull db '[:user/id :user/given-name :user/family-name :user/email
                                          :user/person-id :db/id]
                                     (user-model/user-ref user-ref))]
    (assoc user :user/permissions
           (when id
             (permission-db/user-permissions db id)))))

(defn resolve-user
  "Allways returns db/id for given user"
  [db user]
  (:db/id (du/entity db (user-model/user-ref user))))

(defn user-with-person-id-exists? [db person-id]
  (-> db
      (d/pull '[:db/id] [:user/person-id person-id])
      :db/id
      boolean))

(defn user-info-by-person-id [db person-id]
  (let [user (user-info db {:user/person-id person-id})]
    (when (:db/id user)
      user)))

(defn is-user-deactivated?
  [db user-eid]
  (boolean (:user/deactivated? (d/pull db '[:user/deactivated?] user-eid))))

(defn users-valid-global-permissions
  [db user-id]
  (->> (d/q '[:find (pull ?p [:permission/role])
              :in $ ?u
             :where
             [?u :user/permissions ?p]
             [(missing? $ ?p :permission/projects)]
             [?p :permission/role _]
             [(missing? $ ?p :permission/valid-until)]]
           db user-id)
      (mapv first)))
