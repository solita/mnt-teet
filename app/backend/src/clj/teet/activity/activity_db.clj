(ns teet.activity.activity-db
  (:require [datomic.client.api :as d]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

(defn activity-date-range
  [db activity-id]
  (d/pull db
          '[:activity/estimated-start-date :activity/estimated-end-date]
          activity-id))

(defn lifecycle-id-for-activity-id
  [db activity-id]
  (let [task (du/entity db activity-id)]
    (get-in task [:thk.lifecycle/_activities 0 :db/id])))

(defn valid-task-dates?
  [db activity-id {:task/keys [actual-end-date actual-start-date estimated-end-date estimated-start-date] :as _task}]
  (let [activity-dates (activity-date-range db activity-id)
        dates (filterv some? [actual-end-date actual-start-date estimated-end-date estimated-start-date])]
    (every? (fn [date]
              (and (not (.before date (:activity/estimated-start-date activity-dates)))
                   (not (.after date (:activity/estimated-end-date activity-dates)))))
            dates)))

(defn- manager-transactions [db activity-ids]
  (->> (d/q '[:find ?a ?modified-at ?tx ?ref
              :in $ [?a ...]
              :where
              [?a :activity/manager ?ref ?tx true]
              [?a :meta/modified-at ?modified-at ?tx true]]
            (d/history db)
            activity-ids)
       (map (fn [[a modified-at tx ref]]
              {:activity a
               :modified-at modified-at
               :tx tx
               :ref ref}))))

(defn- manager-period [[a b]]
  (if (nil? b)
    {:manager (:ref a)
     :period [(:modified-at a) nil]}
    {:manager (:ref a)
     :period [(:modified-at a) (:modified-at b)]}))

(defn- manager-transactions->manager-periods [manager-transactions]
  (->> manager-transactions
       (partition 2 1 nil)
       (map manager-period)))

(defn- users-by-id [db db-ids]
  (->> (d/q '[:find (pull ?u [:db/id :user/given-name :user/family-name])
              :in $ [?u ...]]
            db db-ids)
       (mapcat (comp (juxt :db/id identity)
                     first))
       (apply hash-map)))

;; TODO rename manager-histories-by-activity etc, change in tests as well
(defn manager-history [manager-transactions users-by-id]
  (->> manager-transactions
       (group-by :activity)
       (cu/map-vals (comp (partial map #(update % :manager users-by-id))
                          manager-transactions->manager-periods
                          (partial sort-by :modified-at)))))

(defn get-activity-ids-of-project
  "get db ids of nondeleted activities belonging to project"
  [db project-id]
  (->> (d/q '[:find (pull ?a [:db/id :meta/deleted?])
              :in $ ?thk-id
              :where
              [?p :thk.project/id ?thk-id]
              [?p :thk.project/lifecycles ?lc]
              [?lc :thk.lifecycle/activities ?a]]
            db project-id)
       (map first)
       (remove :meta/deleted?)
       (map :db/id)))

(defn get-manager-histories [db project-id]
  (let [activity-ids (get-activity-ids-of-project db project-id)
        txs (manager-transactions db activity-ids)
        users (users-by-id db (->> txs (map :ref) distinct))]
    (manager-history txs users)))
