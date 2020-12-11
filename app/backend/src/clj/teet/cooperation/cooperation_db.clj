(ns teet.cooperation.cooperation-db
  (:require [datomic.client.api :as d]
            [teet.cooperation.cooperation-model :as cooperation-model]
            [clojure.string :as str]
            [teet.util.date :as date]
            [teet.util.datomic :as du]))

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
     (->>
      (for [{id :db/id :as tp} third-parties
            :let [application-ids (applications-to-fetch id)]]
        (merge
         tp
         (when (seq application-ids)
           {:cooperation.3rd-party/applications
            (->> (d/q '[:find (pull ?e attrs)
                        :in $ [?e ...] attrs]
                      db application-ids
                      cooperation-model/application-overview-attrs)
                 (mapv first)
                 (sort-by :cooperation.application/date)
                 reverse)})))
      (sort-by (comp str/lower-case :cooperation.3rd-party/name))
      vec))))

(defn third-party-id-by-name
  "Find 3rd party id in project by its name."
  [db project-eid third-party-name]
  (ffirst (d/q '[:find ?e
                 :where
                 [?e :cooperation.3rd-party/project ?project]
                 [?e :cooperation.3rd-party/name ?name]
                 :in $ ?project ?name]
               db project-eid third-party-name)))

(defn third-party-with-application [db third-party-id application-id]
  (merge
   (d/pull db cooperation-model/third-party-display-attrs
           third-party-id)
   {:cooperation.3rd-party/applications
    [(ffirst (d/q '[:find (pull ?e attrs)
                    :where [?third-party :cooperation.3rd-party/applications ?e]
                    :in $ ?third-party ?e attrs]
                  db third-party-id application-id
                  cooperation-model/application-overview-attrs))]}))

;; This could probably be done with a single datomic query as well
(defn application-matched-activity-id
  "Given project-id and an application with a date, return an activities id that is on going during the dates"
  [db project-id application]
  (let [application-date (:cooperation.application/date application)]
    (->> (:thk.project/lifecycles
           (d/pull db '[{:thk.project/lifecycles
                         [{:thk.lifecycle/activities
                           [:db/id
                            :activity/name
                            :activity/estimated-start-date
                            :activity/estimated-end-date]}]}]
                   [:thk.project/id project-id]))
         (mapcat
           :thk.lifecycle/activities)
         (some
           (fn [{:activity/keys [estimated-start-date
                                 estimated-end-date] :as activity}]
             (when (and (date/date-within? application-date
                                           [estimated-start-date estimated-end-date])
                        (not= (get-in activity [:activity/name :db/ident])
                              :activity.name/land-acquisition))
               activity)))
         :db/id)))

(defn application-project-id [db application-id]
  (get-in (du/entity db application-id)
          [:cooperation.3rd-party/_applications 0 :cooperation.3rd-party/project :thk.project/id]))
