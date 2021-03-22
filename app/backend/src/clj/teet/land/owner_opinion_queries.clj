(ns teet.land.owner-opinion-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.land.owner-opinion-db :as owner-opinion-db]
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [teet.land.owner-opinion-commands :as owner-opinion-commands]
            [teet.land.owner-opinion-export :as owner-opinion-export]
            [teet.project.project-db :as project-db]))

(defn- pull-large-texts [opinions]
  (mapv (partial
          db-api-large-text/with-large-text
          owner-opinion-commands/land-owner-opinion-rich-text-fields)
    opinions))

(defquery :land-owner-opinion/fetch-opinions
  {:doc "Fetch all land owner opinions for a project and unit"
   :context {db :db}
   :args {project-id :project-id
          land-unit-id :land-unit-id}
   :project-id [:thk.project/id project-id]
   :authorization {:land/view-land-owner-opinions {}}}
  (pull-large-texts (owner-opinion-db/owner-opinions db project-id land-unit-id)))


(defquery :land-owner-opinion/opinions-count
  {:doc "Fetch all land owner opinions for a project and unit"
   :context {db :db}
   :args {project-id :project-id
          land-unit-id :land-unit-id}
   :project-id [:thk.project/id project-id]
   :authorization {:land/view-land-owner-opinions {}}}
  (count (owner-opinion-db/owner-opinions db project-id land-unit-id)))

(defquery :land-owner-opinion/export-opinions
  {:doc "Fetch land owner opinions as HTML"
   :context {:keys [db user]}
   :args {:land-owner-opinion/keys [activity type] :as args}
   :project-id (project-db/activity-project-id db activity)
   :authorization {:land/view-land-owner-opinions {}}}
  ^{:format :raw}
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"}
   :body (owner-opinion-export/summary-table db activity type)})
