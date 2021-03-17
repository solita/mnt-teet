(ns teet.land.owner-opinion-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.land.owner-opinion-db :as owner-opinion-db]))

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