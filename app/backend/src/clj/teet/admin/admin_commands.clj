(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [taoensso.timbre :as log]
            [teet.meta.meta-model :as meta-model]
            [teet.user.user-db :as user-db]
            [teet.user.user-model :as user-model]
            [teet.user.user-spec :as user-spec]
            [datomic.client.api :as d]))



#_(defn user-data-from-xroad [new-user-eid current-user-eid]
  (let [xroad-url (environment/config-value :xroad-query-url)
        xroad-instance-id (environment/config-value :xroad-instance-id)
        resp (teet.integration.x-road/perform-rr442-request xroad-url
                                                            {:instance-id xroad-instance-id
                                                             :requesting-eid current-user-eid
                                                             :subject-eid new-user-eid})
        name-valid? (complement blank?)
        name-map {:user/given-name (-> resp :result :Eesnimi)
                  :user/family-name (-> resp :result :Perenimi)}]
    (if (and (= :ok (:status resp)) (every? name-valid? (vals name-map)))
      name-map
      ;; else
      (if (= :ok (:status resp))
        (throw (ex-info "x-road response status ok but user name data not valid" {:names name-map :ok? (:ok resp)}))
        ;; else
        (throw (ex-info "x-road error response" (merge resp {:names name-map})))))))


(defn date-before?
  "a is-before b?"
  [a b]
  (if (and a b)
    (.before a b)
    false))

(defn- redundant-with-existing-permission? [user-info new-permission]
  (let [redundant (fn [existing]
                    (and (= (:permission/role new-permission)
                            (:permission/role existing)) ;; same role
                         (nil? (:permission/valid-to new-permission)) ;; no expiration
                         (nil? (:permission/valid-to existing)) ;; no expiration

                         (date-before? (:permission/valid-from existing)
                                       (:permission/valid-from new-permission))))]

    (->> (:user/permissions user-info)
         (filter redundant)
         not-empty)))

(defn- new-permission [granting-user role date]
  {:user/permissions [(merge (meta-model/creation-meta granting-user)
                             {:db/id "new-permission"
                              :permission/role role
                              :permission/valid-from date})]})

(defcommand :admin/create-user
  {:doc "Create user (although for now can be also used to add global permissions to existing users through admin view)"
   :context {:keys [conn user db]}
   :payload user-data
   :project-id nil
   :pre [(:user/person-id user-data)
         (user-spec/estonian-person-id? (:user/person-id user-data))
         ^{:error :user-already-exists}
         (nil? (user-db/resolve-user db user-data))]
   :authorization {:admin/add-user {}}
   :audit? true
   :transact (let [user-person-id (user-model/normalize-person-id (:user/person-id user-data))
                   global-role (:user/global-role user-data)]
               ;; New user, no need to check
               (println "tx: " (pr-str [(merge (user-model/new-user user-person-id)
                                               (select-keys user-data [:user/email :user/company :user/phone-number])
                                               (meta-model/creation-meta user)
                                               (when global-role
                                                 (new-permission user
                                                                 global-role
                                                                 (java.util.Date.))))]))
               [(merge (user-model/new-user user-person-id)
                       (select-keys user-data [:user/email :user/company :user/phone-number])
                       (meta-model/creation-meta user)
                       (when global-role
                         (new-permission user
                                         global-role
                                         (java.util.Date.))))])})


(defcommand :admin/edit-user
  {:doc "Edit existing users information or global permission"
   :context {:keys [conn user db]}
   :payload form-value
   :project-id nil
   :pre [(:user/person-id form-value)]
   :authorization {:admin/add-user {}}
   :audit? true
   :transact (let [user-values (select-keys form-value [:user/email :user/company :user/phone-number
                                                        :user/person-id])]
               [(merge user-values
                       (meta-model/modification-meta user))
                (list 'teet.user.user-tx/set-global-role
                      user
                      [:user/person-id (:user/person-id form-value)]
                      (:user/global-role form-value))])})


(defcommand :admin/deactivate-user
  {:doc "Mark user as deactivated, this will disable user login and not allow them to be shown in user selections"
   :context {:keys [user db]}
   :payload user-to-deactivate
   :project-id nil
   :pre [(:user/person-id user-to-deactivate)               ;Maybe check that this is an actual user?
         ]
   :audit? true
   :authorization {:admin/remove-user {}}
   :transact [(let [deactivated-user (d/pull db [:db/id] (user-model/user-ref user-to-deactivate))]
                (merge
                  {:db/id (:db/id deactivated-user)
                   :user/deactivated? true}
                  (meta-model/modification-meta user)))]})

(defcommand :admin/reactivate-user
  {:doc "Remove users deactivation"
   :context {:keys [user db]}
   :payload user-to-reactivate
   :project-id nil
   :pre [(:user/person-id user-to-reactivate)               ;Maybe check that this is an actual user?
         ]
   :audit? true
   :authorization {:admin/add-user {}}
   :transact (let [deactivated-user (d/pull db [:db/id] (user-model/user-ref user-to-reactivate))]
               [(merge
                  {:db/id (:db/id deactivated-user)}
                  (meta-model/modification-meta user))
                [:db/retract (:db/id deactivated-user)
                 :user/deactivated? true]])})
