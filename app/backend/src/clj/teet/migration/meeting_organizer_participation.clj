(ns teet.migration.meeting-organizer-participation
  (:require [datomic.client.api :as d]
            [clojure.set :as set]))

(defn migrate-meeting-organizers
  [conn]
  (let [db (d/db conn)
        meetings-with-organizer (into #{}
                                      (map first)
                                      (d/q '[:find (pull ?m [:db/id :meeting/organizer])
                                             :in $
                                                 :where
                                                 [?m :meeting/organizer ?u]
                                                 [?p :participation/in ?m]
                                                 [?p :participation/role :participation.role/organizer]]
                                               db))
        meetings (into #{}
                       (map first)
                       (d/q '[:find (pull ?m [:db/id :meeting/organizer])
                              :in $
                              :where
                              [?m :meeting/organizer ?u]]
                            db))
        meetings-to-migrate (set/difference meetings meetings-with-organizer)]
    (d/transact
      conn
      {:tx-data (vec
                  (for [m meetings-to-migrate]
                    {:participation/participant (get-in m [:meeting/organizer :db/id])
                     :participation/role :participation.role/organizer
                     :participation/in (:db/id m)}))})))
