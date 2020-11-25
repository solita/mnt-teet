(ns teet.cooperation.cooperation-db
  (:require [datomic.client.api :as d]
            [teet.util.collection :as cu]
            [teet.cooperation.cooperation-model :as cooperation-model]))

(defn overview
  "Fetch cooperation overview for a project: returns all third parties with
  their latest application info.

  If all-applications-pred function is given, all applications will be
  returned for third parties whose id matches the predicate."
  ([db project-eid]
   (overview db project-eid (constantly false)))
  ([db project-eid all-applications-pred]
   (let [third-parties
         (mapv first
               (d/q '[:find (pull ?e [:db/id :cooperation.3rd-party/name])
                      :where [?e :cooperation.3rd-party/project ?project]
                      :in $ ?project]
                    db project-eid))

         applications-by-party
         (->> (d/q '[:find ?third-party ?application ?date
                     :where
                     [?third-party :cooperation.3rd-party/applications ?application]
                     [?application :cooperation.application/date ?date]
                     :in $ [?third-party ...]]
                   db (map :db/id third-parties))
              (group-by first))

         applications-to-fetch
         (into {}
               (map (fn [[third-party-id applications]]
                      [third-party-id
                       (map
                        second
                        (if (all-applications-pred third-party-id)
                          ;; Return all applications
                          applications

                          ;; Return only the latest application
                          (take 1 (reverse (sort-by #(nth % 2) applications)))))]))
               applications-by-party)]
     (vec
      (for [{id :db/id :as tp} third-parties
            :let [application-ids (applications-to-fetch id)]]
        (merge
         tp
         {:cooperation.3rd-party/applications
          (mapv first
                (d/q '[:find (pull ?e attrs)
                       :in $ [?e ...] attrs]
                     db application-ids
                     cooperation-model/application-overview-attrs))}))))))

(defn third-party-id-by-name
  "Find 3rd party id in project by its name."
  [db project-eid third-party-name]
  (ffirst (d/q '[:find ?e
                 :where
                 [?e :cooperation.3rd-party/project ?project]
                 [?e :cooperation.3rd-party/name ?name]
                 :in $ ?project ?name]
               db project-eid third-party-name)))
