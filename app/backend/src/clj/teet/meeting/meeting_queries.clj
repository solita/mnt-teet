(ns teet.meeting.meeting-queries
  (:require [teet.project.project-db :as project-db]
            [teet.db-api.core :refer [defquery]]
            [teet.meta.meta-query :as meta-query]
            [teet.project.project-model :as project-model]))


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
