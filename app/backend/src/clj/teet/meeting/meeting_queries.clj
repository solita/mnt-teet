(ns teet.meeting.meeting-queries
  (:require [teet.project.project-db :as project-db]
            [teet.db-api.core :refer [defquery]]
            [teet.meta.meta-query :as meta-query]
            [teet.project.project-model :as project-model]
            [teet.meeting.meeting-db :as meeting-db]
            [datomic.client.api :as d]))


(defn fetch-project-meetings
  [db eid]
  (project-db/project-by-id db eid {:thk.lifecycle/activities
                                    [{:activity/meetings
                                      [:db/id
                                       :meeting/title
                                       :meeting/location
                                       {:meeting/organizer [:user/person-id
                                                            :user/given-name
                                                            :user/family-name]}
                                       :meeting/end :meeting/start]}]}))

(defquery :meeting/project-with-meetings
  {:doc "Fetch project data with project meetings"
   :context {db :db
             user :user}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :authorization {:project/read-info {:eid [:thk.project/id id]
                                       :link :thk.project/owner
                                       :access :read}}}
  (update (meta-query/without-deleted
            db
            (fetch-project-meetings db [:thk.project/id id]))
          :thk.project/lifecycles project-model/sort-lifecycles))

(defquery :meeting/fetch-meeting
  {:doc "Fetch a single meeting info and project info"
   :context {:keys [db user]}
   :args {:keys [activity-id meeting-id]}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:project/read-info {:eid (project-db/activity-project-id db activity-id)
                                       :link :thk.project/owner
                                       :access :read}}}
  {:project (project-db/project-by-id db (project-db/activity-project-id db activity-id))
   :meeting (d/pull db '[:db/id
                         :meeting/title :meeting/location
                         :meeting/start :meeting/end
                         :meeting/organizer
                         {:meeting/agenda [:db/id
                                           :meeting.agenda/topic
                                           :meeting.agenda/body
                                           :meeting.agenda/responsible]}
                         ;; FIXME: all decisions, participants etc
                         ]
           (meeting-db/activity-meeting-id db activity-id meeting-id))})
