(ns teet.land.land-queries
  (:require [teet.db-api.core :refer [defquery]]
            [teet.gis.features :as features]
            [datomic.client.api :as d]
            [teet.integration.x-road :as x-road]
            [clj-time.core :as time]
            [clj-time.coerce :as c]))

(defquery :land/fetch-land-acquisitions
  {:doc "Fetch all land acquisitions and related cadastral units from a project"
   :context {db :db}
   :args {project-id :project-id
          units :units}
   :project-id [:thk.project/id project-id]
   :authorization {}}
  (let [land-acquisitions (mapv first (d/q '[:find (pull ?e [*])
                                       :in $ ?project-id
                                       :where [?e :land-acquisition/project ?project-id]]
                                     db
                                     [:thk.project/id project-id]))
        related-cadastral-units (d/pull db '[:thk.project/related-cadastral-units] [:thk.project/id project-id])]
    (merge related-cadastral-units
           {:land-acquisitions land-acquisitions})))

(defn- project-cadastral-units [db api-url api-secret project-id]
  (let [ctx {:api-url api-url
             :api-secret api-secret}]
    (-> (d/pull db '[:thk.project/related-cadastral-units] [:thk.project/id project-id])
        :thk.project/related-cadastral-units
        (as-> units
            (features/geojson-features-by-id ctx units)
          (map :properties
               (:features units))))))


(defquery :land/estate-info
  {:doc "Fetch estate info from x-road"
   :context {:keys [user]}
   :args {estate-id :estate-id
          project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :config {xroad-instance [:xroad-instance-id]
            xroad-url [:xroad-query-url]}
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id project-id]
                                              :link :thk.project/owner}}}
  (assoc
    (x-road/perform-kinnistu-d-request
      xroad-url
      {:instance-id xroad-instance
       :registriosa-nr estate-id
       :requesting-eid (str "EE" (:user/person-id user))})
    :estate-id estate-id))

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
            api-secret [:auth :jwt-secret]}
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id id]
                                              :link :thk.project/owner}}}
  (let [units (project-cadastral-units db api-url api-secret id)
        estates (into #{}
                      (map :KINNISTU)
                      units)]
    {:estates estates
     :units (mapv
              (fn [{:keys [MOOTVIIS MUUDET] :as unit}]
                (assoc unit :quality (cond
                                       (and (= MOOTVIIS "mõõdistatud, L-EST")
                                            (not (time/before? (c/from-string MUUDET) (time/date-time 2018 01 01))))
                                       :good
                                       (and (= MOOTVIIS "mõõdistatud, L-EST")
                                            (time/before? (c/from-string MUUDET) (time/date-time 2018 01 01)))
                                       :questionable
                                       :else
                                       :bad)))
              units)}))

