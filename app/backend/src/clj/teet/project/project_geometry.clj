(ns teet.project.project-geometry
  "Code for working with project geometries stored in PostgreSQL.
  Provides functions for calling the PostgREST API."
  (:require [teet.road.road-query :as road-query]
            [teet.util.geo :as geo]
            [teet.integration.integration-id :as integration-id]
            [teet.log :as log]
            [jeesql.core :refer [defqueries]]
            [teet.environment :as environment]))

(defqueries "teet/project/project_geometry.sql")

(defn update-project-geometries!
  "Update project geometries in PostgreSQL.
  Calls store_entity_info in PostgREST API."
  [{:keys [wfs-url]} projects]
  (let [road-part-cache (atom {})]
    (doseq [{id :integration/id
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
      (environment/call-with-pg-connection
       (fn [conn]
         (store-entity-info!
          conn
          {:id (str (integration-id/uuid->number id))
           :type "project"
           :tooltip (or project-name name)
           :geometry (geo/line-string-to-wkt geometry)}))))
    projects))
