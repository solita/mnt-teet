(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.environment :as environment]
            [clojure.string :refer [blank? starts-with?]]
            [teet.integration.x-road]
            teet.user.user-spec))

(defn- new-user []
  {:user/id (java.util.UUID/randomUUID)
   :user/roles [:user]})

(defn ensure-ee-prefix [eid]
  (if (starts-with? eid "EE")
    eid
    (str "EE" eid)))

(defn user-data-from-xroad [new-user-eid current-user-eid]
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

(defcommand :admin/create-user
  {:doc "Create user"
   :context {:keys [conn user]}
   :payload user-data
   :project-id nil
   :authorization {:admin/add-user {}}
   :transact [;; (log/info "admin/create-user: current user" (:user/person-id user))
              (merge (new-user)
                     (select-keys user-data [:user/person-id])
                     (when-let [p (:user/add-global-permission user-data)]
                       {:user/permissions [{:db/id "new-permission"
                                            :permission/role p
                                            :permission/valid-from (java.util.Date.)}]})
                     #_(user-data-from-xroad (:user/person-id user-data) (:user/person-id user)))]})
