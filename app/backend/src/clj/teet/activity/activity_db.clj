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
