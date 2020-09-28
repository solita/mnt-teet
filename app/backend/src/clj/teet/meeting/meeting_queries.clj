(ns teet.meeting.meeting-queries
  (:require [teet.project.project-db :as project-db]
            [teet.db-api.core :refer [defquery]]
            [teet.meta.meta-query :as meta-query]
            [teet.meeting.meeting-db :as meeting-db]
            [teet.user.user-model :as user-model]
            [datomic.client.api :as d]
            [clojure.walk :as walk]
            [teet.util.date :as du]
            [teet.meeting.meeting-model :as meeting-model]))


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
  (mapv first
        (d/q '[:find (pull ?m [* :activity/_meetings])
               :where
               [?a :activity/meetings ?m]
               [?m :meeting/start ?start]
               [(.before ?start ?today)]
               [(missing? $ ?m :meta/deleted?)]
               :in $ ?a ?today]
             db
             activity-eid
             (du/start-of-today))))

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
  (meta-query/without-deleted
    db
    {:project (fetch-project-meetings db (project-db/activity-project-id db activity-id)) ;; This ends up pulling duplicate information, could be refactored
     :meeting (d/pull
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
                (meeting-db/activity-meeting-id db activity-id meeting-id))}))


(defquery :meeting/activity-meeting-history
  {:doc "Fetch past meetings for an activity"
   :context {:keys [db user]}
   :args {:keys [activity-eid]}
   :project-id (project-db/activity-project-id db activity-eid)
   :authorization {}}
  (activity-past-meetings db activity-eid))
