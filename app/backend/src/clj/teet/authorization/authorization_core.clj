(ns teet.authorization.authorization-core
  "Authorize user actions based on their role in contracts, or global roles given through admin interface"
  (:require [clojure.java.io :as io]
            [teet.authorization.authorization-db :as authorization-db]
            [clojure.set :as set]
            [teet.user.user-model :as user-model]
            [teet.user.user-db :as user-db]
            [teet.environment :as environment]))

(defonce authorization-matrix
         (delay
           (some-> "contract-authorization.edn"
                   io/resource
                   slurp
                   read-string)))

(defn general-project-access?
  [db user project-id]
  (let [user-ref (user-model/user-ref user)
        user-global-roles (user-db/users-valid-global-permissions db user-ref)
        user-roles-for-project (authorization-db/user-roles-for-project db user-ref project-id)]
    (-> (set/union user-global-roles user-roles-for-project)
        seq
        boolean)))

(defmulti special-authorization
  :action)

(defmethod special-authorization
  :default
  [_]
  false)

(defn authorized-for-action?
  "Check rights either based on the target, company or contract
  Given either company or a target find if the user has the right.
  If neither is given only checks for global permissions
  Entity option is only checked in the special-authorization multimethod"
  [{:keys [db user action target company contract _entity-id] :as opts}]
  {:pre [(and db (keyword? action) (action @authorization-matrix))
         (<= (count (select-keys opts [target company contract]))
             1)]}
  (if-not (environment/feature-enabled? :contract-partners)
    false
    (or (special-authorization opts)
        (let [authorized-roles (action @authorization-matrix)
              user-ref (user-model/user-ref user)
              user-global-roles (->> (user-db/users-valid-global-permissions db user-ref)
                                     (mapv :permission/role)
                                     set)
              specific-roles
              (cond target
                    (authorization-db/user-roles-for-target db user-ref target)
                    company
                    (authorization-db/user-roles-for-company db user-ref company)
                    contract
                    (authorization-db/user-roles-for-contract db user-ref contract)
                    :else
                    nil)
              roles (set/union user-global-roles specific-roles)]
          (-> (set/intersection authorized-roles roles)
              seq
              boolean)))))

