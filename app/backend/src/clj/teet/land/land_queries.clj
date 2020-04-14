(ns teet.land.land-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.gis.features :as features]
            [datomic.client.api :as d]
            [teet.integration.x-road :as x-road]))

(defquery :land/fetch-land-acquisitions
  {:doc "Fetch all land acquisitions related to proejct"
   :context {db :db}
   :args {project-id :project-id
          units :units}
   :project-id [:thk.project/id project-id]
   :authorization {}}
  (let [land-acquisitions (d/q '[:find (pull ?e [*])
                                 :in $
                                 :where [?e :land-acquisition/project ?project-id]]
                               db
                               project-id)]
    (mapv
      first
      land-acquisitions)))

(defn- project-cadastral-unit-estates [db api-url api-shared-secret project-id]
  (let [ctx {:api-url api-url
             :api-shared-secret api-shared-secret}]
    (-> (d/pull db '[:thk.project/related-cadastral-units] [:thk.project/id project-id])
        :thk.project/related-cadastral-units
        (as-> units
            (features/geojson-features-by-id ctx units)
          (map (comp :KINNISTU :properties) (:features units)))
        distinct)))


(defquery :land/related-project-estates
  {:doc "Fetch estates that are related to a given project's cadastral units.
Will fetch the cadastral unit information from PostgREST to determine
the estate numbers for all selected cadastral units.

Then it will query X-road for the estate information."
   :context {:keys [db user]}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :config {xroad-instance [:xroad-instance-id]
            xroad-url [:xroad-query-url]
            api-url [:api-url]
            api-shared-secret [:auth :jwt-secret]}
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id id]
                                              :link :thk.project/owner}}}
  (into {}

        ;; Fetch the X-road estate info for each estate number
        (map (juxt identity
                   #(x-road/perform-kinnistu-d-request xroad-url xroad-instance %)))

        ;; Fetch unique estate numbers for project's related cadastral units
        (project-cadastral-unit-estates db api-url api-shared-secret id)))
