(ns teet.migration.user-permissions
  (:require [datomic.client.api :as d]
            [teet.user.user-tx :as user-tx]))


;; Pull users with global permissions that have no valid until
;; {:admin 1 :manager 2 :internal-consultant 3 :external-consultant 4} ---
;; sort by role importance and take first
;; create transaction with call to (set-global-role) for all users with their hightest permission role

(def permission-importance
  {:admin 1
   :manager 2
   :internal-consultant 3
   :external-consultant 4})

(defn remove-overlapping-global-rights
  "Migration to go over all users and their permissions to see if there are multiple simultaneous global permissions
  for the user."
  [conn]
  (let [db (d/db conn)
        user-permissions
        (d/q '[:find (pull ?u [* {:user/permissions [*]}])
               :in $
               :where
               [?u :user/person-id _]
               [?u :user/permissions ?p]
               [(missing? $ ?p :permission/projects)]
               [(missing? $ ?p :permission/valid-until)]]
             db)
        transaction
        (->> user-permissions
             (mapv first)
             (mapv (fn [user]
                     (update user :user/permissions
                             (fn [permissions]
                               ;; Go through all users permissions and only take the ones which are not invalidated
                               ;; Or which are related to some projects
                               (->> permissions
                                    (filterv (fn [perm]
                                               (when (and
                                                       (nil? (:permission/projects perm))
                                                       (nil? (:permission/valid-until perm)))
                                                 perm)))
                                    (sort-by (comp permission-importance :permission/role))
                                    first)))))
             (mapv (fn [user]
                     {:db/id (:db/id user)
                      :permission/role (get-in user [:user/permissions :permission/role])}))
             (mapv (fn [user-perm]
                     (user-tx/set-global-role db nil (:db/id user-perm) (:permission/role user-perm))))
             (mapv first)
             (filterv (fn [perm]
                        (when (not-empty perm)
                          perm))))]
    (d/transact
      conn
      {:tx-data transaction})))


(defn pos-number->sequence-number [conn]
  (let [db (d/db conn)
        files-with-pos
        (map first
             (d/q '[:find (pull ?f [:db/id :file/pos-number])
                    :where
                    [?act :activity/name :activity.name/land-acquisition]
                    [?act :activity/tasks ?task]
                    [?task :task/files ?f]
                    [?f :file/pos-number _]] db))]
    (d/transact
      conn
      {:tx-data (vec (for [f files-with-pos]
                       {:db/id (:db/id f)
                        :file/sequence-number (:file/pos-number f)}))})))
