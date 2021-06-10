(ns teet.project.project-geometry
  "Code for working with project geometries stored in PostgreSQL.
  Provides functions for calling the PostgREST API."
  (:require [org.httpkit.client :as http]
            [cheshire.core :as cheshire]
            [teet.road.road-query :as road-query]
            [teet.util.geo :as geo]
            [teet.integration.integration-id :as integration-id]
            [jeesql.core :refer [defqueries]]
            [teet.integration.postgresql :refer [with-connection with-transaction]]
            [teet.log :as log]))

(declare store-entity-info! delete-stale-projects!)
(defqueries "teet/project/project_geometry.sql")

(defn update-project-geometries!
  "Update project geometries in PostgreSQL.
  Calls store_entity_info in stored procedure.
  If `:delete-stale-projects?` is true, deletes any projects' geometries
  not in `projects`. This should only be set when updating geometries
  for all projects."
  [{:keys [wfs-url delete-stale-projects?]
    :or {delete-stale-projects? false}} projects]
  (log/info "Updating project geometries for " (count projects) " projects."
            (when delete-stale-projects?
              " Deleting other stale projects."))
  (let [road-part-cache (atom {})
        project-ids (into #{}
                          (map (comp integration-id/uuid->number
                                     :integration/id))
                          projects)
        project-geometry-updates
        (doall ; force WFS fetched so we don't do them while holding tx open
         (for [{id :integration/id
                :thk.project/keys [project-name name road-nr carriageway
                                   start-m end-m
                                   custom-start-m custom-end-m]}
               projects
               :when (and id
                          (integer? (or custom-start-m start-m))
                          (integer? (or custom-end-m end-m))
                          (integer? road-nr)
                          (integer? carriageway))
               :let [geometry (road-query/fetch-road-geometry
                               {:wfs-url wfs-url
                                :cache-atom road-part-cache}
                               road-nr carriageway
                               (or custom-start-m start-m)
                               (or custom-end-m end-m))]]
           {:id (str (integration-id/uuid->number id))
            :type "project"
            :tooltip (or project-name name)
            :geometry (geo/line-string-to-wkt geometry)}))]
    (with-connection db
      (with-transaction db
        (when delete-stale-projects?
          (delete-stale-projects! db {:project-ids project-ids}))
        (doseq [upd project-geometry-updates]
          (store-entity-info! db upd))))

     projects))
