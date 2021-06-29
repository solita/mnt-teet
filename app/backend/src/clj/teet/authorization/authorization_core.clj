(ns teet.authorization.authorization-core
  "Authorize user actions based on their role in contracts, or global roles given through admin interface"
  (:require [clojure.java.io :as io]
            [teet.authorization.authorization-db :as authorization-db]
            [clojure.set :as set]
            [teet.user.user-model :as user-model]
            [teet.user.user-db :as user-db]))

(defonce authorization-matrix
         (delay
           (some-> "contract-authorization.edn"
                   io/resource
                   slurp
                   read-string)))

(defn project-read-access?
  [db user project-id]
  (let [user-ref (user-model/user-ref user)
        user-global-roles (user-db/users-valid-global-permissions db user-ref)
        user-roles-for-project (authorization-db/user-roles-for-project db user-ref project-id)]
    (-> (set/union user-global-roles user-roles-for-project)
        seq
        boolean)))

(defn authorized-for-action?
  "Check rights either based on the target or company
  Given either company or a target find if the user has the right "
  [{:keys [db user action target company]}]
  {:pre [(or target company)
         (not (and target company))]}
  (let [authorized-roles (action @authorization-matrix)
        user-ref (user-model/user-ref user)
        user-global-roles (->> (user-db/users-valid-global-permissions db user-ref)
                               (mapv :permission/role)
                               set)
        specific-roles
        (cond target
              (authorization-db/user-roles-for-target db user-ref target)
              company
              (authorization-db/user-roles-for-company db user-ref company))
        roles (set/union user-global-roles specific-roles)]

    (println "specific-roles: " specific-roles)
    (println "authorized-roles " authorized-roles)
    (-> (set/intersection authorized-roles roles)
        seq
        boolean)))

