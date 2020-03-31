(ns teet.admin.admin-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.user.user-roles :as user-roles]
            [teet.environment :as environment]
            [clojure.string :refer [blank?]]
            [teet.integration.x-road]
            teet.user.user-spec))

(defn- new-user []
  {:user/id (java.util.UUID/randomUUID)
   :user/roles [:user]})

(defn user-data-from-xroad [person-id]
  (let [xroad-url (environment/config-value :xroad-query-url)
        xroad-instance-id (environment/config-value :xroad-instance-id)
        resp (teet.integration.x-road/perform-rr442-request
              xroad-url xroad-instance-id person-id)
        name-valid? (complement blank?)
        name-map {:user/given-name (-> resp :result :Eesnimi)
                  :user/family-name (-> resp :result :Perenimi)}]
    (if (and (= :ok (:status resp)) (every? name-valid? (vals name-map)))
      name-map
      ;; else
      (throw (ex-info "x-road user name data not valid" {:names name-map :ok? (:ok resp)})))))

(defcommand :admin/create-user
  {:doc "Create user"
   :context {:keys [conn user]}
   :payload user-data
   :pre [(user-roles/require-role user :admin)]
   :project-id nil
   :authorization {:admin/add-user {}}
   :transact [(merge (new-user)
                     (select-keys user-data [:user/id :user/person-id :user/roles])
                     (user-data-from-xroad (:user/person-id user-data)))]})
