(ns teet.geo.geo-queries
  "Queries that serve data from PostGIS functions."
  (:require [teet.db-api.core :refer [defquery]]
            [jeesql.core :refer [defqueries]]))

(defqueries "teet/geo/geo_queries.sql")

(defquery :geo/entity-pins
  {:doc "Get pins for entity center points"
   :context {pg :pg}
   :args {type :type}
   :project-id nil
   :authorization {}}
  ^{:format :raw}
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (geojson-entity-pins pg {:type type})})

(defquery :geo/entities
  {:doc "Return entity geometries for given entities"
   :context {pg :pg}
   :args {ids :ids}
   :project-id nil
   :authorization {}}
  ^{:format :raw}
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (geojson-entities
          pg
          {:ids (into-array Long (mapv #(Long/parseLong %) ids))})})

(defquery :geo/mvt
  {:doc "Get tile layer MVT"
   :context {pg :pg}
   :args {:keys [type xmin ymin xmax ymax]}
   :project-id nil
   :authorization {}}
  ^{:format :raw}
  {:status 200
   :headers {"Content-Type" "application/octet-stream"}
   :body (mvt-entities pg {:type type
                           :xmin xmin :ymin ymin
                           :xmax xmax :ymax ymax})})
