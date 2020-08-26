(ns teet.meeting.meeting-queries
  (:require [teet.project.project-db :as project-db]
            [teet.db-api.core :refer [defquery]]
            [teet.meta.meta-query :as meta-query]
            [datomic.client.api :as d]
            [teet.meeting.meeting-db :as meeting-db]))

(defquery :meeting/project-with-meetings
  {:doc "Fetch project info (for project navigator) and project's meetings"
   :context {:keys [db user]}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :authorization {:project/read-info {:eid [:thk.project/id id]
                                       :link :thk.project/owner
                                       :access :read}}}
  (let [project (meta-query/without-deleted
                 db (project-db/project-by-id db [:thk.project/id id]))]
    (assoc project
           :meetings (meeting-db/meetings db
                                          '[[?p :thk.project/lifecycles ?lc]
                                            [?lc :thk.lifecycle/activities ?act]
                                            [?act :activity/meetings ?meeting]]
                                          {'?p [:thk.project/id id]}))))
