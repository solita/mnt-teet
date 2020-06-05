(ns teet.activity.activity-db
  (:require [datomic.client.api :as d]
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

(defn- manager-transactions [db activity-id]
  (->> (d/q '[:find ?modified-at ?tx ?ref
              :in $ ?activity-id
              :where
              ;; Todo change these to :db/id and :activity/manager
              [?a :thk.project/id ?activity-id]
              [?a :thk.project/manager ?ref ?tx true]
              [?a :meta/modified-at ?modified-at ?tx]
              [?tx :db/txInstant ?instant]]
            (d/history db)
            activity-id)
       (map (fn [[modified-at tx ref]]
              {:modified-at modified-at
               :tx tx
               :ref ref}))
       (sort-by :modified-at)))

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

(defn manager-history [manager-transactions users-by-id]
  (->> manager-transactions
       manager-transactions->manager-periods
       (map #(update % :manager users-by-id))))

(defn fetch-manager-history [db activity-id]
  (let [txs (manager-transactions db activity-id)
        users (users-by-id db (map :ref txs))]
    (manager-history txs users)))
