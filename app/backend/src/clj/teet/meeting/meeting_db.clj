(ns teet.meeting.meeting-db
  (:require [datomic.client.api :as d]))

(def default-meeting-columns [:meeting/start-time :meeting/topic])

(defn project-meetings
  "Return all meetings in all activities "
  ([db project-eid]
   (project-meetings db project-eid default-meeting-columns))
  ([db project-eid pull-pattern]
   (d/q '[:find (pull ?m pull-pattern)
          :where [?m :activity/_meeting ?act]
          [?act :thk.lifecycle/_activities ?lc]
          [?lc :thk.project/_lifecycles ?project]
          :in $ ?project pull-pattern]
        db project-eid pull-pattern)))
