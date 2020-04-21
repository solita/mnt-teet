(ns teet.activity.activity-db
  (:require [datomic.client.api :as d]))

(defn activity-date-range
  [db activity-id]
  (d/pull db
          '[:activity/estimated-start-date :activity/estimated-end-date]
          activity-id))
