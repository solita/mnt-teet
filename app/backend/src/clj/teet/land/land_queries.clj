(ns teet.land.land-queries
  (:require [teet.db-api.core :refer [defquery audit]]
            [teet.gis.features :as features]
            [datomic.client.api :as d]
            [teet.integration.x-road.property-registry :as property-registry]
            [teet.integration.x-road.business-registry :as business-registry]
            [clj-time.core :as time]
            [clj-time.coerce :as c]
            [clojure.walk :as walk]
            [teet.land.land-db :as land-db]
            [teet.file.file-db :as file-db]
            [teet.util.collection :as cu]
            [teet.meta.meta-query :as meta-query]
            [teet.link.link-db :as link-db])
  (:import (java.time LocalDate)
           (java.time.format DateTimeFormatter)))


(defn- datomic->form
  "Format data to a format suitable for frontend form.
  Does following transformations:

  - Stringify bigdec values.
  - Turn enum maps with db/ident keyword to keywords"
  [compensation]
  (->> compensation
       (walk/prewalk
         (fn [x]
           (cond
             (decimal? x) (str x)
             (and (map? x) (contains? x :db/ident)) (:db/ident x)
             :else x)))))


(defquery :land/fetch-land-acquisitions
  {:doc "Fetch all land acquisitions and related cadastral units from a project"
   :context {db :db}
   :args {project-id :project-id}
   :project-id [:thk.project/id project-id]}
  (let [land-acquisitions (mapv first (d/q '[:find (pull ?e [*])
                                       :in $ ?project-id
                                       :where [?e :land-acquisition/project ?project-id]]
                                     db
                                     [:thk.project/id project-id]))
        related-cadastral-units (d/pull db '[:thk.project/related-cadastral-units] [:thk.project/id project-id])]
    (datomic->form
     (merge related-cadastral-units
            {:land-acquisitions land-acquisitions}))))

(defn project-cadastral-units [db api-url api-secret project-id]
  (let [ctx {:api-url api-url
             :api-secret api-secret}]
    (-> (d/pull db '[:thk.project/related-cadastral-units] [:thk.project/id project-id])
        :thk.project/related-cadastral-units
        (as-> units
            (features/geojson-features-by-id ctx units)
          (map :properties
               (:features units))))))

(defn- with-quality [{:keys [MOOTVIIS MUUDET] :as unit}]
  (assoc unit :quality
         (cond
           (and (= MOOTVIIS "mõõdistatud, L-EST")
                (not (time/before? (c/from-string MUUDET) (time/date-time 2018 01 01))))
           :good
           (and (= MOOTVIIS "mõõdistatud, L-EST")
                (time/before? (c/from-string MUUDET) (time/date-time 2018 01 01)))
           :questionable
           :else
           :bad)))

(defn- with-estate [estates {:keys [KINNISTU] :as unit}]
  (assoc unit :estate (assoc (get estates KINNISTU) :estate-id KINNISTU)))

(defquery :land/related-project-estates
  {:doc "Fetch estates that are related to a given project's cadastral units.
Will fetch the cadastral unit information from PostgREST to determine
the estate numbers for all selected cadastral units.

Then it will query X-road for the estate information."
   :context {:keys [db user]}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :config {xroad-instance [:xroad :instance-id]
            xroad-url [:xroad :query-url]
            xroad-subsystem [:xroad :kr-subsystem-id]
            api-url [:api-url]
            api-secret [:auth :jwt-secret]}}
  (let [units (project-cadastral-units db api-url api-secret id)
        estates (into #{}
                      (map :KINNISTU)
                      units)
        estate-infos (property-registry/fetch-all-estate-info
                     {:xroad-url xroad-url
                      :xroad-kr-subsystem-id xroad-subsystem
                      :instance-id xroad-instance
                      :requesting-eid (str "EE" (:user/person-id user))
                      :api-url api-url
                      :api-secret api-secret}
                     estates)
        filtered-estate-infos (cu/map-vals
                               property-registry/active-jagu34-only
                               estate-infos)]
    (audit :land/related-project-estates {:thk.project/id id})
    {:estates estates
     :units (mapv (comp with-quality
                        (partial with-estate filtered-estate-infos))
                  units)}))

(defquery :land/fetch-estate-compensations
  {:doc "Fetch estate compensations in a given project. Returns map with estate id as the key
and the compensation info as the value."
   :context {:keys [db user]}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]}
  (datomic->form
    (into {}
          (comp
          (map first)
          (map (fn [{estate-id :estate-procedure/estate-id :as compensation-form}]
                 [estate-id compensation-form])))
          (meta-query/without-deleted db (land-db/project-estate-procedures db [:thk.project/id id])))))

(defquery :land/estate-owner-info
  {:doc "Fetch information about an estate owner."
   :context {:keys [db user]}
   :args {id :thk.project/id
          :keys [business-id person-id]}
   :project-id [:thk.project/id id]
   :config {xroad-instance [:xroad :instance-id]
            xroad-url [:xroad :query-url]
            xroad-subsystem [:xroad :kr-subsystem-id]}}
  (let [response
        (business-registry/perform-detailandmed-request
         xroad-url {:business-id business-id
                    :instance-id xroad-instance
                    :requesting-eid (:user/person-id user)})]
    (walk/prewalk
     (fn [x]
       (if (instance? LocalDate x)
         (.format x DateTimeFormatter/ISO_LOCAL_DATE)
         x))
     response)))

(defquery :land/files-by-sequence-number
  {:doc "Fetch land acquisition tasks' file infos by sequence number"
   :context {:keys [db user]}
   :args {id :thk.project/id
          sequence-number :file/sequence-number}
   :project-id [:thk.project/id id]}
  (file-db/land-files-by-project-and-sequence-number
   db user [:thk.project/id id] sequence-number))

(defquery :land/file-count-by-sequence-number
  {:doc "Fetch land acquisition tasks' file counts by sequence number"
   :context {:keys [db user]}
   :args {id :thk.project/id
          sequence-number :file/sequence-number}
   :project-id [:thk.project/id id]
   :pre [(some? sequence-number)]}
  (count (file-db/land-files-by-project-and-sequence-number
          db user [:thk.project/id id] sequence-number)))

(defmethod link-db/fetch-external-link-info :estate [_user _ id]
  {:estate-id id})
