(ns teet.user.user-db
  (:require [datomic.client.api :as d]
            [teet.permission.permission-db :as permission-db]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]
            [clojure.string :as str]))

(defn user-info
  "Fetch user information with current valid permissions."
  [db user-ref]
  (let [{id :db/id :as user} (d/pull db '[:user/id :user/given-name :user/family-name :user/email
                                          :user/person-id :db/id :user/last-login :user/phone-number]
                                     (user-model/user-ref user-ref))]
    (assoc user :user/permissions
           (when id
             (permission-db/user-permissions db id)))))

(defn user-display-info
  "Fetch user display info (name and email) only"
  [db user-ref]
  (d/pull db user-model/user-info-attributes (user-model/user-ref user-ref)))

(defn resolve-user
  "Allways returns db/id for given user"
  [db user]
  (:db/id (du/entity db (user-model/user-ref user))))

(defn user-has-logged-in?
  "Check whether the user has logged in or not"
  [db user-ref]
  (-> db
      (d/pull '[:user/last-login] (user-model/user-ref user-ref))
      :user/last-login
      boolean))

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

(def user-query-rules
  '[[(user-by-name ?u ?search)
     [?u :user/id _]
     [?u :user/given-name ?given]
     [(missing? $ ?u :user/deactivated?)]
     [?u :user/family-name ?family]
     [(str ?given " " ?family) ?full-name]
     [(.toLowerCase ?full-name) ?full-name-lower]
     [(.contains ^String ?full-name-lower ?search)]]])

(defn find-user-by-name [db name]
  (d/q '[:find (pull ?u [:db/id :user/id :user/given-name :user/family-name :user/email :user/person-id])
         :where
         (user-by-name ?u ?name)
         :in $ % ?name]
       db
       user-query-rules
       (str/lower-case name)))

(defn find-all-users [db]
  (d/q '[:find (pull ?e [:db/id :user/id :user/given-name :user/family-name :user/email :user/person-id])
         :where
         [(missing? $ ?e :user/deactivated?)]
         [?e :user/id _]
         [?e :user/family-name _]]
       db))
