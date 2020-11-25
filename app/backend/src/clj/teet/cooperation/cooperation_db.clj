(ns teet.cooperation.cooperation-db
  (:require [datomic.client.api :as d]
            [teet.util.collection :as cu]
            [teet.cooperation.cooperation-model :as cooperation-model]))

(defn overview
  "Fetch cooperation overview for a project: returns all third parties with
  their latest application info."
  [db project-eid]
  (let [third-parties
        (mapv first
              (d/q '[:find (pull ?e [:db/id :cooperation.3rd-party/name])
                     :where [?e :cooperation.3rd-party/project ?project]
                     :in $ ?project]
                   db project-eid))

        latest-application-ids
        (->> (d/q '[:find ?third-party ?application ?date
                    :where
                    [?third-party :cooperation.3rd-party/applications ?application]
                    [?application :cooperation.application/date ?date]
                    :in $ [?third-party ...]]
                  db (map :db/id third-parties))
             (group-by first)
             (cu/map-vals
              (fn [applications]
                (second
                 (first (reverse (sort-by #(nth % 2) applications)))))))]
    (vec
     (for [{id :db/id :as tp} third-parties
           :let [application-id (latest-application-ids id)]]
       (merge tp
              (when application-id
                {:cooperation.3rd-party/applications
                 [(d/pull db cooperation-model/application-overview-attrs
                          application-id)]}))))))
