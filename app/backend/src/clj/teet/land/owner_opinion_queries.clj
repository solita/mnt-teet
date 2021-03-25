(ns teet.land.owner-opinion-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.land.owner-opinion-db :as owner-opinion-db]
            [teet.land.owner-opinion-export :as owner-opinion-export]
            [teet.project.project-db :as project-db]))

(defquery :land-owner-opinion/fetch-opinions
  {:doc "Fetch all land owner opinions for a project and unit"
   :context {db :db}
   :args {project-id :project-id
          land-unit-id :land-unit-id}
   :project-id [:thk.project/id project-id]
   :authorization {:land/view-land-owner-opinions {}}}
  (owner-opinion-db/owner-opinions db project-id land-unit-id))


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
   :args {:land-owner-opinion/keys [activity type test?] :as args}
   :project-id (project-db/activity-project-id db activity)
   :config {xroad-instance [:xroad :instance-id]
            xroad-url [:xroad :query-url]
            xroad-subsystem [:xroad :kr-subsystem-id]
            api-url [:api-url]
            api-secret [:auth :jwt-secret]}
   :authorization {:land/view-land-owner-opinions {}}}
  ^{:format :raw}
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"}
   :body (let [config {:xroad-instance xroad-instance
                       :xroad-url xroad-url
                       :xroad-subsystem xroad-subsystem
                       :api-url api-url
                       :api-secret api-secret}]
           (owner-opinion-export/owner-opinion-summary-table
             db activity type
             (owner-opinion-export/fetch-external-unit-infos
               db
               (project-db/activity-project-id db activity)
               config)))})
