(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.environment :as environment]
            [clojure.string :refer [blank? starts-with?]]
            [taoensso.timbre :as log]
            [datomic.client.api :as d]            
            teet.user.user-spec))

(defn- new-user []
  {:user/id (java.util.UUID/randomUUID)
   :user/roles [:user]})

(defn ensure-ee-prefix [eid]
  (if (starts-with? eid "EE")
    eid
    (str "EE" eid)))

#_(defn user-data-from-xroad [new-user-eid current-user-eid]
  (let [xroad-url (environment/config-value :xroad-query-url)
        xroad-instance-id (environment/config-value :xroad-instance-id)
        resp (teet.integration.x-road/perform-rr442-request xroad-url
                                                            {:instance-id xroad-instance-id
                                                             :requesting-eid (ensure-ee-prefix current-user-eid)
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


(defn redundant-with-existing-permission? [db person-id new-permission current-date]
  (let [redundant (fn [existing]
                    (and (= (:permission/role new-permission) (:permission/role existing)) ;; same role
                         (nil? (:permission/valid-to new-permission)) ;; no expiration
                         (nil? (:permission/valid-to existing)) ;; no expiration
                         (.before (:permission/valid-from existing) current-date)))        
        q-res (d/q '[:find (pull ?perms [*])
                     :in $ ?pid
                     :where [?user :user/person-id ?pid]
                     [?user :user/permissions ?perms]] db "EE123123123X")
        existing-perms (map first q-res)
        redundants (filterv redundant existing-perms)]
    (not-empty redundants)))

(defcommand :admin/create-user
  {:doc "Create user"
   :context {:keys [conn user db]}
   :payload user-data
   :project-id nil
   :authorization {:admin/add-user {}}
   :transact [;; (log/info "admin/create-user: current user" (:user/person-id user))
              (merge (new-user)
                           (select-keys user-data [:user/person-id])
                           (when-let [p (:user/add-global-permission user-data)]
                             
                             (let [current-date (java.util.Date.)
                                   new-permission {:user/permissions [{:db/id "new-permission"
                                                                       :permission/role p
                                                                       :permission/valid-from current-date}]}
                                   redundant? (redundant-with-existing-permission?
                                               db
                                               (:user/person-id user-data)
                                               (first (:user/permissions new-permission))
                                               current-date)]
                               (if redundant?
                                 (log/info "request to add redundant permission, skipping - new permission was" new-permission)
                                 new-permission)))
                           #_(user-data-from-xroad (:user/person-id user-data) (:user/person-id user)))]})
