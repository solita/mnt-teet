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
            [teet.util.collection :as cu])
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
   :project-id [:thk.project/id project-id]
   :authorization {}}
  (let [land-acquisitions (mapv first (d/q '[:find (pull ?e [*])
                                       :in $ ?project-id
                                       :where [?e :land-acquisition/project ?project-id]]
                                     db
                                     [:thk.project/id project-id]))
        related-cadastral-units (d/pull db '[:thk.project/related-cadastral-units] [:thk.project/id project-id])]
    (datomic->form
     (merge related-cadastral-units
            {:land-acquisitions land-acquisitions}))))

(defn- project-cadastral-units [db api-url api-secret project-id]
  (let [ctx {:api-url api-url
             :api-secret api-secret}]
    (-> (d/pull db '[:thk.project/related-cadastral-units] [:thk.project/id project-id])
        :thk.project/related-cadastral-units
        (as-> units
            (features/geojson-features-by-id ctx units)
          (map :properties
               (:features units))))))

(defn filter-ended
  "Used to filter all burdens/mortgages that are no longer deemed interesting"
  [details]
  (filterv #(not= "Lõpetatud" (:oiguse_seisund_tekst %)) details))


(defquery :land/estate-info
  {:doc "Fetch estate info from local cache or X-road"
   :context {:keys [user]}
   :args {estate-id :estate-id
          project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :config {xroad-instance [:xroad :instance-id]
            xroad-url [:xroad :query-url]
            xroad-subsystem [:xroad :kr-subsystem-id]
            api-url [:api-url]
            api-secret [:auth :jwt-secret]}
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id project-id]
                                              :link :thk.project/owner}}}
  (let [x-road-response (property-registry/fetch-estate-info
                         {:xroad-url xroad-url
                          :xroad-kr-subsystem-id xroad-subsystem
                          :instance-id xroad-instance
                          :requesting-eid (str "EE" (:user/person-id user))
                          :api-url api-url
                          :api-secret api-secret}
                         estate-id)]
    (if (= (:status x-road-response) :ok)
      (-> x-road-response
          property-registry/active-jagu34-only
          (assoc :estate-id estate-id))
      (throw (ex-info "Invalid xroad response" {:error :invalid-x-road-response
                                                :response x-road-response})))))

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
            api-secret [:auth :jwt-secret]}
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id id]
                                              :link :thk.project/owner}}}
  (let [units (project-cadastral-units db api-url api-secret id)
        estates (into #{}
                      (map :KINNISTU)
                      units)
        estate-info (property-registry/fetch-all-estate-info
                     {:xroad-url xroad-url
                      :xroad-kr-subsystem-id xroad-subsystem
                      :instance-id xroad-instance
                      :requesting-eid (str "EE" (:user/person-id user))
                      :api-url api-url
                      :api-secret api-secret}
                     estates)
        filtered-estate-info (cu/map-vals (fn [estate]
                                            (-> estate
                                                (update :jagu3 filter-ended)
                                                (update :jagu4 filter-ended)))
                                          estate-info)]

    (audit :land/related-project-estates {:thk.project/id id})
    {:estates estates
     :units (mapv (comp with-quality
                        (partial with-estate filtered-estate-info))
                  units)}))

(defquery :land/fetch-estate-compensations
  {:doc "Fetch estate compensations in a given project. Returns map with estate id as the key
and the compensation info as the value."
   :context {:keys [db user]}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id id]
                                              :link :thk.project/owner}}}
  (datomic->form
   (into {}
         (comp
          (map first)
          (map (fn [{estate-id :estate-procedure/estate-id :as compensation-form}]
                 [estate-id compensation-form])))
         (land-db/project-estate-procedures db [:thk.project/id id]))))

(defquery :land/estate-owner-info
  {:doc "Fetch information about an estate owner."
   :context {:keys [db user]}
   :args {id :thk.project/id
          :keys [business-id person-id]}
   :project-id [:thk.project/id id]
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id id]
                                              :link :thk.project/owner}}
   :config {xroad-instance [:xroad :instance-id]
            xroad-url [:xroad :query-url]
            xroad-subsystem [:xroad :kr-subsystem-id]}}
  (let [response
        (business-registry/perform-detailandmed-request
         xroad-url {:business-id business-id
                    :instance-id xroad-instance})]
    (walk/prewalk
     (fn [x]
       (if (instance? LocalDate x)
         (.format x DateTimeFormatter/ISO_LOCAL_DATE)
         x))
     response)))

(defquery :land/files-by-position-number
  {:doc "Fetch files by position number"
   :context {:keys [db user]}
   :args {id :thk.project/id
          pos :file/pos-number}
   :project-id [:thk.project/id id]
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id id]
                                              :link :thk.project/owner}}}
  (file-db/files-by-project-and-pos-number
   db [:thk.project/id id] pos))

(defquery :land/file-count-by-position-number
  {:doc "Fetch files by position number"
   :context {:keys [db user]}
   :args {id :thk.project/id
          pos :file/pos-number}
   :project-id [:thk.project/id id]
   :authorization {:land/view-cadastral-data {:eid [:thk.project/id id]
                                              :link :thk.project/owner}}
   :pre [(some? pos)]}
  (file-db/file-count-by-project-and-pos-number
    db [:thk.project/id id] pos))
