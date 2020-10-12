(ns teet.meeting.meeting-queries
  (:require [teet.project.project-db :as project-db]
            [teet.db-api.core :refer [defquery]]
            [teet.meta.meta-query :as meta-query]
            [teet.meeting.meeting-db :as meeting-db]
            [teet.user.user-model :as user-model]
            [datomic.client.api :as d]
            [clojure.walk :as walk]
            [teet.util.date :as du]
            [teet.util.string :as string]
            [teet.link.link-db :as link-db]
            [teet.link.link-model :as link-model]))


(defn project-upcoming-meetings
  [db project-eid]
  (d/q '[:find (pull ?m [* :activity/_meetings])
         :where
         [?p :thk.project/lifecycles ?l]
         [?l :thk.lifecycle/activities ?a]
         [?a :activity/meetings ?m]
         [?m :meeting/start ?start]
         [(.after ?start ?today)]
         [(missing? $ ?m :meta/deleted?)]
         :in $ ?p ?today]
       db
       project-eid
       (du/start-of-today)))

(defn activity-past-meetings
  [db activity-eid]
  (->> (d/q '[:find (pull ?m [* :activity/_meetings])
              :where
              [?a :activity/meetings ?m]
              [?m :meeting/start ?start]
              [(.before ?start ?today)]
              [(missing? $ ?m :meta/deleted?)]
              :in $ ?a ?today]
            db
            activity-eid
            (du/start-of-today))
       (mapv first)
       (sort-by :meeting/start #(.after %1 %2))))

(defn project-past-meetings
  [db project-id]
  (->> (d/q '[:find (pull ?m [* {:activity/_meetings [:activity/name :db/id]}])
              :in $ ?project ?today
              :where
              [?project :thk.project/lifecycles ?l]
              [?l :thk.lifecycle/activities ?a]
              [?a :activity/meetings ?m]
              [?m :meeting/start ?start]
              [(.before ?start ?today)]
              [(missing? $ ?m :meta/deleted?)]]
            db
            project-id
            (du/start-of-today))
       (mapv first)
       (mapv #(assoc % :meeting/activity-name (get-in % [:activity/_meetings 0 :activity/name]))) ;; This is done to have the activity name in easier place for frontend
       (sort-by :meeting/start)
       reverse))

(defn matching-decision-ids
  [search-term meetings]
  (set
    (for [m meetings
          a (:meeting/agenda m)
          d (:meeting.agenda/decisions a)
          :let [candidate-text (str (:meeting/title m)
                                    " "
                                    (:meeting/number m)
                                    " "
                                    (:meeting/location m)
                                    " "
                                    (:meeting.agenda/topic a)
                                    " "
                                    (:meeting.decision/body d)
                                    " "
                                    (:meeting.decision/number d))]
          :when (string/contains-words? candidate-text search-term)]
      (:db/id d))))

(defn filter-decisions
  [decision-ids meetings]
  (for [m meetings
        :when (some decision-ids
                    (map :db/id
                      (mapcat
                        :meeting.agenda/decisions
                        (:meeting/agenda m))))]
    (assoc m :meeting/agenda
             (for [a (:meeting/agenda m)
                   :when (some decision-ids
                               (map :db/id
                                    (:meeting.agenda/decisions a)))]
               (assoc a :meeting.agenda/decisions
                        (for [d (:meeting.agenda/decisions a)
                              :when (decision-ids (:db/id d))]
                          d))))))

(defn activity-decisions
  [db user activity-id search-term]
  (let [meetings
        (link-db/fetch-links
          db user
          #(contains? % :meeting.decision/body)
          (meta-query/without-deleted
            db
            (->> (d/q '[:find
                        (pull ?m [* :activity/_meetings
                                  {:meeting/agenda
                                   [* {:meeting.agenda/decisions
                                       [:db/id :meeting.decision/body
                                        :meeting.decision/number
                                        {:file/_attached-to
                                         [:db/id :file/name
                                          :meta/created-at
                                          {:meta/creator [:user/given-name :user/family-name]}]}]}]}
                                  {:review/_of
                                   [{:review/reviewer [:user/given-name
                                                       :user/family-name]}]}])
                        (max ?cr)
                        :where
                        [?a :activity/meetings ?m]
                        [?m :meeting/agenda ?ag]
                        [?m :meeting/locked? true]
                        [?ag :meeting.agenda/decisions ?d]
                        [?r :review/of ?m]
                        [?r :meta/created-at ?cr]
                        :in $ ?a]
                      db activity-id)
                 (map #(assoc (first %) :meeting/locked-at (second %)))
                 (sort-by :meeting/start)
                 reverse)))
        decision-ids (matching-decision-ids search-term meetings)]
    (filter-decisions decision-ids meetings)))

(defn project-decisions
  [db user project-id search-term]
  (let [meetings (link-db/fetch-links
                   db user
                   #(contains? % :meeting.decision/body)
                   (meta-query/without-deleted
                     db
                     (->> (d/q '[:find
                                 (pull ?m [* {:activity/_meetings [:activity/name
                                                                   :db/id]}
                                           {:meeting/agenda
                                            [* {:meeting.agenda/decisions
                                                [:db/id :meeting.decision/body
                                                 :meeting.decision/number
                                                 {:file/_attached-to
                                                  [:db/id :file/name
                                                   :meta/created-at
                                                   {:meta/creator [:user/given-name :user/family-name]}]}]}]}
                                           {:review/_of
                                            [{:review/reviewer [:user/given-name
                                                                :user/family-name]}]}])
                                 (max ?cr)
                                 :where
                                 [?p :thk.project/lifecycles ?l]
                                 [?l :thk.lifecycle/activities ?a]
                                 [?a :activity/meetings ?m]
                                 [?m :meeting/agenda ?ag]
                                 [?m :meeting/locked? true]
                                 [?ag :meeting.agenda/decisions ?d]
                                 [?r :review/of ?m]
                                 [?r :meta/created-at ?cr]
                                 :in $ ?p]
                               db project-id)
                          (map #(assoc (first %) :meeting/locked-at (second %)))
                          (sort-by :meeting/start)
                          reverse)))
        decision-ids (matching-decision-ids search-term meetings)]
    (filter-decisions decision-ids meetings)))

(defn fetch-project-meetings
  [db eid]
  (let [activity-meetings (group-by
                            #(-> %
                                 :activity/_meetings
                                 first
                                 :db/id)
                            (mapv first
                                  (project-upcoming-meetings db eid)))]
    (walk/postwalk
      (fn [e]
        (if-let [activity-meeting (and (map? e) (get activity-meetings (:db/id e)))]
          (assoc e :activity/meetings activity-meeting)
          e))
      (project-db/project-by-id db eid {}))))

(defquery :meeting/project-with-meetings
  {:doc "Fetch project data with project meetings"
   :context {db :db
             user :user}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :authorization {:project/read-info {:eid [:thk.project/id id]
                                       :link :thk.project/owner
                                       :access :read}}}
  (meta-query/without-deleted
    db
    (fetch-project-meetings db [:thk.project/id id])))

(def attachments {:file/_attached-to
                  [:db/id :file/name
                   :meta/created-at
                   {:meta/creator [:user/given-name :user/family-name]}]})

(defquery :meeting/fetch-meeting
  {:doc "Fetch a single meeting info and project info"
   :context {:keys [db user]}
   :args {:keys [activity-id meeting-id]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:project/read-info {:eid (project-db/activity-project-id db activity-id)
                                       :link :thk.project/owner
                                       :access :read}}}
  (link-db/fetch-links
   db user
   #(or (contains? % :meeting.agenda/body)
        (contains? % :meeting.decision/body))
   (meta-query/without-deleted
    db
    {:project (fetch-project-meetings db (project-db/activity-project-id db activity-id)) ;; This ends up pulling duplicate information, could be refactored
     :meeting (assoc
               (d/pull
                db
                `[:db/id
                  :meeting/title :meeting/location
                  :meeting/start :meeting/end
                  :meeting/number
                  {:meeting/organizer ~user-model/user-listing-attributes}
                  {:meeting/agenda [:db/id
                                    :meeting.agenda/topic
                                    :meeting.agenda/body
                                    {:meeting.agenda/decisions
                                     [:db/id :meeting.decision/body
                                      :meeting.decision/number
                                      ~attachments]}
                                    {:meeting.agenda/responsible ~user-model/user-listing-attributes}
                                    ~attachments]}
                  {:review/_of [:db/id
                                :review/comment
                                :review/decision
                                :meta/created-at
                                {:review/reviewer ~user-model/user-listing-attributes}]}
                  {:participation/_in
                   [:db/id
                    :participation/role
                    {:participation/participant ~user-model/user-listing-attributes}]}]
                (meeting-db/activity-meeting-id db activity-id meeting-id))
               :meeting/locked?
               (meeting-db/locked? db meeting-id))}
    (fn [entity]
      (contains? entity :link/to)))))


(defquery :meeting/activity-meeting-history
  {:doc "Fetch past meetings for an activity"
   :context {:keys [db user]}
   :args {:keys [activity-id]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {}}
  (activity-past-meetings db activity-id))

(defquery :meeting/activity-decision-history
  {:doc "Fetch all the decisions for activity matching the given string"
   :context {:keys [db user]}
   :args {:keys [activity-id
                 search-term]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {}}
  (activity-decisions db user activity-id search-term))


(defquery :meeting/project-meeting-history
  {:doc "Fetch all the meetings from the history of the project"
   :context {:keys [db user]}
   :args {:keys [project-id]}
   :project-id project-id
   :authorization {}}
  (project-past-meetings db project-id))

(defquery :meeting/project-decision-history
  {:doc "Fetch all decisions for project matching the given string"
   :context {:keys [db user]}
   :args {:keys [project-id
                 search-term]}
   :project-id project-id
   :authorization {}}
  (project-decisions db user project-id search-term))
