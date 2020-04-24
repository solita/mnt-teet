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
