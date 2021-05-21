(ns teet.geo.geo-queries
  "Queries that serve data from PostGIS functions."
  (:require [teet.db-api.core :refer [defquery]]
            [jeesql.core :refer [defqueries]]
            [teet.util.coerce :refer [->long]]
            [datomic.client.api :as d]))

(defqueries "teet/geo/geo_queries.sql")

(defn ->ids [ids]
  (into-array Long (mapv ->long ids)))

(defn- response [type body]
  ^{:format :raw}
  {:status 200
   :headers {"Content-Type" (case type
                              :geojson "application/geojson"
                              :mvt "application/octet-stream")}
   :body body})

(defquery :geo/entity-pins
  {:doc "Get pins for entity center points"
   :context {pg :pg}
   :args {:keys [ids type]}
   :project-id nil
   :authorization {}}
  (response
   :geojson
   (cond
     type
     (geojson-entity-pins-for-type pg {:type type})

     ids
     (geojson-entity-pins-for-ids pg {:ids (->ids ids)}))))

(defquery :geo/entities
  {:doc "Return entity geometries for given entities"
   :context {pg :pg}
   :args {ids :ids}
   :project-id nil
   :authorization {}}
  (response
   :geojson
   (geojson-entities
    pg
    {:ids (->ids ids)})))

(defquery :geo/mvt
  {:doc "Get tile layer MVT"
   :context {pg :pg}
   :args {:keys [type xmin ymin xmax ymax]}
   :project-id nil
   :authorization {}}
  (response
   :mvt
   (mvt-entities pg {:type type
                     :xmin xmin :ymin ymin
                     :xmax xmax :ymax ymax})))

(defquery :geo/project-related-restrictions
  {:doc "Get project related restrictions as GeoJSON."
   :context {:keys [db pg]}
   :args {id :db/id}
   :project-id id
   :authorization {:project/read-info {}}}
  (response
   :geojson
   (let [ids (mapv first
                   (d/q '[:find ?rr
                          :where [?p :thk.project/related-restrictions ?rr]
                          :in $ ?p]
                        db id))]
     (geojson-features-by-id pg {:ids (into-array String ids)}))))

(defquery :geo/project-related-cadastral-units
  {:doc "Get project related cadastral units as GeoJSON."
   :context {:keys [db pg]}
   :args {id :db/id}
   :project-id id
   :authorization {:project/read-info {}}}
  (response
   :geojson
   (let [ids (mapv first
                   (d/q '[:find ?cu
                          :where [?p :thk.project/related-cadastral-units ?cu]
                          :in $ ?p]
                        db id))]
     (geojson-features-by-id pg {:ids (into-array String ids)}))))
