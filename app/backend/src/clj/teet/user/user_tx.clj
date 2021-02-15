(ns teet.user.user-tx
  (:require [datomic.client.api :as d]
            [teet.meta.meta-model :as meta-model]
            [datomic.ion :as ion])
  (:import (java.util Date)))

(defn ensure-unique-email [db email tx-data]
  (if-not email
    tx-data
    (let [{db-after :db-after} (d/with db {:tx-data tx-data})
          users (d/q '[:find ?u
                       :where
                       [?u :user/id _]
                       [?u :user/email ?email]
                       :in $ ?email] db-after email)]
      (if (> (count users) 1)
        (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                     :cognitect.anomalies/message "Duplicate email"
                     :teet/error :email-address-already-in-use})
        tx-data))))

(defn set-global-role
  [db modifying-user user-eid role]
  (let [now (Date.)
        user-id (:db/id (d/pull db [:db/id] user-eid))
        permissions (mapv
                      first
                      (d/q '[:find (pull ?p [:permission/role :db/id])
                             :in $ ?u ?now
                             :where
                             [?u :user/permissions ?p]
                             [(missing? $ ?p :permission/projects)]
                             [(missing? $ ?p :permission/valid-until)]]
                           db
                           user-id
                           now))
        existing-role (some
                        #(when (= (:permission/role %) role)
                           %)
                        permissions)]
    (->> (for [p permissions
               :when (not= p existing-role)]
           (merge {:db/id (:db/id p)
                   :permission/valid-until now}
                  (when modifying-user                      ;; NIL in migration
                    (meta-model/modification-meta modifying-user))))
         (concat
           (when (and (not existing-role) (some? role))
             [{:db/id user-id
               :user/permissions
               [(merge
                  {:db/id (str "new-global-permission-" user-id) ;; TO support multiple user permissions per tx
                   :permission/role role
                   :permission/valid-from now}
                  (when modifying-user                      ;; NIL in migration
                    (meta-model/creation-meta modifying-user)))]}]))
         (remove nil?)
         vec)))
