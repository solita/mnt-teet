(ns teet.cooperation.cooperation-db
  (:require [datomic.client.api :as d]
            [teet.cooperation.cooperation-model :as cooperation-model]
            [clojure.string :as str]
            [teet.util.date :as date]
            [teet.util.datomic :as du]
            [clj-time.core :as t]))

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
               (d/q '[:find (pull ?e [:db/id :teet/id :cooperation.3rd-party/name])
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

(def third-party-by-teet-id
  "Find 3rd party by :teet/id. Returns entity :db/id or nil."
  (partial du/entity-by-teet-id :cooperation.3rd-party/name))

(def application-by-teet-id
  (partial du/entity-by-teet-id :cooperation.application/type))




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

(defn third-party-application-task
  [db third-party-id application-id]
  (ffirst
   (d/q '[:find (pull ?task [*]) ;; todo: add some param definitions
          :in $ ?third-part ?application-id
          :where
          [?third-part :cooperation.3rd-party/applications ?applications]
          [(missing? $ ?applications :meta/deleted?)]
          [?applications :cooperation.application/activity ?activities]
          [(missing? $ ?activities :meta/deleted?)]
          [?activities :activity/tasks ?task]
          [(missing? $ ?task :meta/deleted?)]
          [?task :task/type :task.type/no-objection-coordination]]
        db third-party-id application-id)))

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
          [:cooperation.3rd-party/_applications 0 :cooperation.3rd-party/project :db/id]))

(defn application-activity-id [db application-id]
  (get-in (du/entity db application-id)
    [:cooperation.application/activity :db/id]))

(defn application-3rd-party [db application-id]
  (get-in (du/entity db application-id)
    [:cooperation.3rd-party/_applications 0 :cooperation.3rd-party/name]))

(defn application-3rd-party-uuid [db application-id]
  (first (d/q '[:find ?id
                :in $ ?application-id
                :keys uuid
                :where [?cooperation :cooperation.3rd-party/applications ?application-id]
                [?cooperation :teet/id ?id]]
           db application-id)))

(defn application-uuid
  [db application-id]
  (let [application-uuid (get-in (du/entity db application-id) [:teet/id])]
    {:uuid application-uuid}))

(defn response-project-id [db response-id]
  ;(def *args [db response-id])
  (get-in (du/entity db response-id)
          [:cooperation.application/_response 0 :cooperation.3rd-party/_applications 0 :cooperation.3rd-party/project :db/id]))

(defn applications-to-be-expired
  "Returns all Applications to be expired in the given number of days"
  [db days]
  (d/q '[:find ?application ?third-party ?date ?application-expiration-date
         :keys application-id third-party-id date application-expiration-date
         :where [?third-party :cooperation.3rd-party/applications ?application]
         [?application :cooperation.application/date ?date]
         [?application :cooperation.application/response ?response]
         [?response :cooperation.response/valid-until ?application-expiration-date]
         [(< ?application-expiration-date ?deadline)]
         (not-join [?application]
           [?notification :notification/target ?application]
           [?notification :notification/type :notification.type/cooperation-application-expired-soon])
         :in $ ?deadline]
    db (date/inc-days (date/now) (Integer/valueOf days))))