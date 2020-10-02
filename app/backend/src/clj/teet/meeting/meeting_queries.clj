(ns teet.meeting.meeting-queries
  (:require [teet.project.project-db :as project-db]
            [teet.db-api.core :refer [defquery]]
            [teet.meta.meta-query :as meta-query]
            [teet.meeting.meeting-db :as meeting-db]
            [teet.user.user-model :as user-model]
            [datomic.client.api :as d]
            [clojure.walk :as walk]
            [teet.util.date :as du]
            [teet.meeting.meeting-model :as meeting-model]
            [teet.localization :refer [with-language tr-enum]]
            [teet.util.string :as string]
            [teet.link.link-db :as link-db]))


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

(defn activity-decisions
  [db activity-id]
  (->> (d/q '[:find
              (pull ?m [* :activity/_meetings
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
       reverse))

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

(def links {:link/_from [:link/to :link/type]})

(defquery :meeting/fetch-meeting
  {:doc "Fetch a single meeting info and project info"
   :context {:keys [db user]}
   :args {:keys [activity-id meeting-id]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:project/read-info {:eid (project-db/activity-project-id db activity-id)
                                       :link :thk.project/owner
                                       :access :read}}}
  (meta-query/without-deleted
    db
    {:project (fetch-project-meetings db (project-db/activity-project-id db activity-id)) ;; This ends up pulling duplicate information, could be refactored
     :meeting (link-db/expand-links
               db
               (assoc
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
                                       ~attachments
                                       ~links]}
                                     {:meeting.agenda/responsible ~user-model/user-listing-attributes}
                                     ~attachments
                                     ~links]}
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
                (meeting-db/locked? db meeting-id)))}))


(defquery :meeting/activity-meeting-history
  {:doc "Fetch past meetings for an activity"
   :context {:keys [db user]}
   :args {:keys [activity-eid]}
   :project-id (project-db/activity-project-id db activity-eid)
   :authorization {}}
  (activity-past-meetings db activity-eid))

(defquery :meeting/activity-decision-history
  {:doc "Fetch all the decisions for activity"
   :context {:keys [db user]}
   :args {:keys [activity-id]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {}}
  (activity-decisions db activity-id))

(defn link-from->project [db [type id]]
  (case type
    :meeting-agenda (project-db/agenda-project-id db id)
    :meeting-decision (project-db/decision-project-id db id)))

(defquery :meeting/search-link-task
  {:doc "Search tasks that can be linked to meeting"
   :context {:keys [db user]}
   :args {:keys [from text lang]}
   :project-id (link-from->project db from)
   :authorization {}}
  (with-language lang
    (let [project (link-from->project db from)

          all-project-tasks
          (d/q '[:find (pull ?t [:task/type :db/id])
                 :where
                 [?p :thk.project/lifecycles ?l]
                 [?l :thk.lifecycle/activities ?a]
                 [?a :activity/tasks ?t]
                 [(missing? $ ?t :meta/deleted)]
                 :in $ ?p]
               db project)

          matching-project-tasks
          (into []
                (comp
                 (map first)
                 (map #(assoc % :searchable-text (tr-enum (:task/type %))))
                 (filter #(string/contains-words? (:searchable-text %)
                                                  text))
                 (map :db/id))
                all-project-tasks)]

      (mapv
       first
       (d/q '[:find (pull ?t [:db/id :task/type
                              :task/estimated-start-date
                              :task/estimated-end-date
                              {:task/assignee [:user/given-name :user/family-name]}
                              {:activity/_tasks [:activity/name]}])
              :in $ [?t ...]]
            db matching-project-tasks)))))
