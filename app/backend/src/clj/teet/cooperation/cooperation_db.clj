(ns teet.cooperation.cooperation-db
  (:require [datomic.client.api :as d]
            [teet.cooperation.cooperation-model :as cooperation-model]
            [clojure.string :as str]
            [teet.util.date :as date]
            [teet.util.datomic :as du]
            [teet.project.task-model :as task-model]
            [teet.entity.entity-db :as entity-db]
            [teet.util.collection :as cu]
            [taoensso.timbre :as log]))

(defn filters->query-parts
  [filters]
  (for [[key val] (cu/without-nils
                    (dissoc filters :shortcut :third-party-name))]
    (case key
      :response-type
      {:where '[[?application :cooperation.application/response-type ?response-type]]
       :in '[?response-type]
       :args [val]}
      :application-type
      {:where '[[?application :cooperation.application/type ?application-type]]
       :in '[?application-type]
       :args [val]}
      :response-status
      {:where '[[?application :cooperation.application/response ?response]
                [?response :cooperation.response/status ?response-status]]
       :in '[?response-status]
       :args [val]}
      :project-activity
      {:where '[[?application :cooperation.application/activity ?activity]
                [?activity :activity/name ?project-activity]]
       :in '[?project-activity]
       :args [val]}
      :cooperation-opinion-status
      {:where '[[?application :cooperation.application/opinion ?opinion]
                [?opinion :cooperation.opinion/status ?opinion-status]]
       :in '[?opinion-status]
       :args [val]})))

(def filters-targeting-applications
  [:response-type :application-type :response-status :project-activity :cooperation-opinion-status])

(defn filter-out-empty-third-parties?
  "Used to check if the current set of filters should result in removal of empty third parties"
  [filters]
  (some #(contains? filters %) filters-targeting-applications))

(defn overview
  "Fetch cooperation overview for a project: returns all third parties with
  their latest application info.

  If all-applications-pred function is given, all applications will be
  returned for third parties whose id matches the predicate."
  [{:keys [db project-eid all-applications-pred search-filters]
    :or {all-applications-pred (constantly false)
         search-filters {}}}]
  (let [{:keys [third-party-name]
         :or {third-party-name ""}} search-filters
        all-applications-pred (if (= (:shortcut search-filters) :all-applications)
                                (constantly true)
                                all-applications-pred)

        filters (filters->query-parts search-filters)

        third-parties
        (mapv first
              (d/q '[:find (pull ?third-party [:db/id :teet/id :cooperation.3rd-party/name])
                     :where
                     [?third-party :cooperation.3rd-party/project ?project]
                     [?third-party :cooperation.3rd-party/name ?name]
                     [(.toLowerCase ^String ?name) ?lower-name]
                     [(.contains ?lower-name ?third-party-name)]
                     :in $ ?project ?third-party-name]
                   db project-eid (str/lower-case third-party-name)))

        applications-by-party
        (->> (d/q {:query {:find '[?third-party ?application ?date]
                           :where
                           (into '[[?third-party :cooperation.3rd-party/applications ?application]
                                   [?application :cooperation.application/date ?date]
                                   [(missing? $ ?application :meta/deleted?)]]
                                 (mapcat :where filters))
                           :in (into '[$ [?third-party ...]] (mapcat :in filters))}
                   :args (into [db (map :db/id third-parties)] (mapcat :args filters))})
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
            :let [application-ids (applications-to-fetch id)]
            ;; Only return third parties when we either allow finding of empty third parties
            ;; Or they contain applications
            :when (or (not (filter-out-empty-third-parties? search-filters))
                      (some? application-ids))]
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
      vec)))

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
  (partial entity-db/entity-by-teet-id :cooperation.3rd-party/name))

(def application-by-teet-id
  (partial entity-db/entity-by-teet-id :cooperation.application/type))




(defn third-party-with-application [db third-party-id application-id]
  (merge
   (d/pull db cooperation-model/third-party-display-attrs
           third-party-id)
   {:cooperation.3rd-party/applications
    [(ffirst (d/q '[:find (pull ?e attrs)
                    :where [?third-party :cooperation.3rd-party/applications ?e]
                           [(missing? $ ?e :meta/deleted?)]
                    :in $ ?third-party ?e attrs]
                  db third-party-id application-id
                  cooperation-model/application-overview-attrs))]}))

(defn third-party-application-task
  "Get the first related task (if any) that has code \"KK\" and can be submitted.
  If no tasks added to the related lifecycle activity return nil
  If there is a task that can not be submitted return map with error message"
  [db application-id]
  (let [tasks
        (mapv first
          (d/q '[:find (pull ?task [* {:file.part/_task [*]}])
                 :in $ ?application-entity
                 :where
                 [?application-entity :cooperation.application/activity ?activity]
                 [(missing? $ ?activity :meta/deleted?)]
                 [?activity :activity/tasks ?task]
                 [(missing? $ ?task :meta/deleted?)]
                 [?task :task/type ?task-type]
                 [?task-type :filename/code "KK"]]
            db application-id))
        can-be-submitted (filter task-model/can-submit? tasks)]
    (if (and
          (seq tasks)
          (empty? can-be-submitted))
      {:error-message "Task can not be submitted"}
      (first can-be-submitted))))

;; This could probably be done with a single datomic query as well
(defn application-matched-activity-id
  "Given project-id and an application with a date, return an activities id that is on going during the dates"
  [db project-id application]
  (let [application-date (:cooperation.application/date application)]
    (assert (some? application-date))
    (->> (mapv
           first
           (d/q '[:find (pull ?ac [:db/id
                                   :activity/estimated-start-date
                                   :activity/estimated-end-date])
                  :in $ ?project
                  :where
                  [?project :thk.project/lifecycles ?lc]
                  [(missing? $ ?lc :meta/deleted?)]
                  (not [?ac :activity/name :activity.name/land-acquisition])
                  [?lc :thk.lifecycle/activities ?ac]
                  [(missing? $ ?ac :meta/deleted?)]]
                db [:thk.project/id project-id]))
         (some
           (fn [{:activity/keys [estimated-start-date
                                 estimated-end-date] :as activity}]
             (when (date/date-within? application-date
                                      [estimated-start-date estimated-end-date])
               activity)))
         :db/id)))

(defn application-has-same-date-as-db?
  "Given project-id and an application with a date, return an activities id that is on going during the dates"
  [db project-id application]
  (let [provided-application-date (:cooperation.application/date application)
        db-application-date (:cooperation.application/date (du/entity db (:db/id application)))]
    (log/debug "comparing dates" provided-application-date db-application-date)
    (= provided-application-date db-application-date)))

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
         [(missing? $ ?application :meta/deleted?)]
         [?application :cooperation.application/date ?date]
         [?application :cooperation.application/response ?response]
         [?response :cooperation.response/valid-until ?application-expiration-date]
         [(< ?application-expiration-date ?deadline)]
         (not-join [?application]
           [?notification :notification/target ?application]
           [?notification :notification/type :notification.type/cooperation-application-expired-soon])
         :in $ ?deadline]
       db (date/inc-days (date/now) (Integer/valueOf days))))


(defn- ->third-party-id [id]
  (if (uuid? id)
    [:teet/id id]
    id))

(defn third-party-project-id
  "Return project id for the third party. Third party id
  can either be a UUID (:teet/id) or long (:db/id)."
  [db third-party-id]
  (ffirst
   (d/q '[:find ?p
          :where [?tp :cooperation.3rd-party/project ?p]
          :in $ ?tp]
        db (->third-party-id third-party-id))))

(defn has-applications?
  "Check if third party has applications."
  [db third-party-id]
  (boolean
   (seq
    (d/q '[:find ?a
           :where
           [?tp :cooperation.3rd-party/applications ?a]
           [(missing? $ ?a :meta/deleted?)]
           :in $ ?tp]
         db (->third-party-id third-party-id)))))

(defn application-editable?
  "Does the application have a third party response?"
  [db application-id]
  (cooperation-model/application-editable? (d/pull db
                                                   [:cooperation.application/opinion
                                                    :cooperation.application/response]
                                                   application-id)))
