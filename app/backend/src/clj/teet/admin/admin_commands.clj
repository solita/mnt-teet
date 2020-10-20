(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [taoensso.timbre :as log]
            [teet.meta.meta-model :as meta-model]
            [teet.user.user-db :as user-db]
            [teet.user.user-model :as user-model]
            [teet.user.user-spec :as user-spec]))



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

(defn- add-permission-to-existin-user-tx [granting-user user-info global-permission]
  (merge (select-keys user-info [:db/id])
         (let [permission-map (new-permission granting-user
                                              global-permission
                                              (java.util.Date.))
               redundant? (redundant-with-existing-permission?
                           user-info
                           (first (:user/permissions permission-map)))]
           (if redundant?
             (log/info "request to add redundant permission, skipping - new permission was" permission-map)
             permission-map))))

(defcommand :admin/create-user
  {:doc "Create user (although for now can be also used to add global permissions to existing users through admin view)"
   :context {:keys [conn user db]}
   :payload user-data
   :project-id nil
   :pre [(:user/person-id user-data)
         (user-spec/estonian-person-id? (:user/person-id user-data))]
   :authorization {:admin/add-user {}}
   :audit? true
   :transact (let [user-person-id (user-model/normalize-person-id (:user/person-id user-data))
                   user-info (user-db/user-info-by-person-id db user-person-id)
                   global-permission (:user/add-global-permission user-data)]
               (if user-info
                 ;; User exists, check for redundant permissions
                 [(if global-permission
                    (add-permission-to-existin-user-tx user user-info global-permission)
                    {})]
                 ;; New user, no need to check
                 [(merge (user-model/new-user user-person-id)
                         (when global-permission
                           (new-permission user
                                           global-permission
                                           (java.util.Date.))))]))})
