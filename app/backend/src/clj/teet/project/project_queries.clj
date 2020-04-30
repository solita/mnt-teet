(ns teet.project.project-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.project.project-model :as project-model]
            [teet.permission.permission-db :as permission-db]
            [datomic.client.api :as d]
            [clojure.string :as str]
            [teet.meta.meta-query :as meta-query]
            [teet.project.project-db :as project-db]
            [teet.util.collection :as cu]
            [teet.task.task-db :as task-db]
            [teet.project.task-model :as task-model]
            [dk.ative.docjure.spreadsheet :as spreadsheet]
            [ring.util.io :as ring-io]
            [teet.environment :as environment]
            [teet.gis.features :as features]
            [teet.road.road-query :as road-query]
            [teet.gis.entity-features :as entity-features]
            [teet.log :as log]))

(defquery :thk.project/db-id->thk-id
  {:doc "Fetch THK project id for given entity :db/id"
   :context {db :db}
   :args {id :db/id}
   :project-id nil
   :authorization {}}
  (-> db
      (d/pull [:thk.project/id] id)
      :thk.project/id))

(defn maybe-fetch-task-files [project db task-id]
  (if-not task-id
    project
    (cu/update-path
     project
     [:thk.project/lifecycles some? ; all lifecycles
      :thk.lifecycle/activities some? ; all activities
      :activity/tasks #(= task-id (:db/id %))] ; matching task

     ;; Fetch and assoc the tasks
     assoc :task/files (task-db/files-for-task db task-id))))


(defn tasks-with-statuses
  [tasks]
  (map
    task-model/task-with-status
    tasks))

(defn maybe-update-activity-tasks
  [project activity-id]
  (if-not activity-id
    project
    (cu/update-path
      project
      [:thk.project/lifecycles some?
       :thk.lifecycle/activities #(= activity-id (str (:db/id %)))]
      update :activity/tasks tasks-with-statuses)))

(defquery :thk.project/fetch-project
  {:doc "Fetch project information"
   :context {db :db}
   :args {:thk.project/keys [id]
          task-id :task-id
          activity-id :activity-id}
   :project-id [:thk.project/id id]
   :authorization {:project/project-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner
                                          :access :read}}}
  (let [project (meta-query/without-deleted
                  db
                  (project-db/project-by-id db [:thk.project/id id]))]
    (-> project
        (assoc :thk.project/permitted-users
               (project-model/users-with-permission
                (permission-db/valid-project-permissions db (:db/id project))))
        (update :thk.project/lifecycles project-model/sort-lifecycles)
        (update :thk.project/lifecycles
                (fn [lifecycle]
                  (map #(update % :thk.lifecycle/activities project-model/sort-activities) lifecycle)))
        (maybe-fetch-task-files db task-id)
        (maybe-update-activity-tasks activity-id))))



(defn- maps->sheet [maps]
  (let [headers (sort (reduce into #{}
                              (map keys maps)))]
    (into [(mapv name headers)]
          (map (apply juxt headers))
          maps)))

(defn- feature-collection->sheet-data [feature-collection]
  (->> feature-collection
       :features
       (map :properties)
       maps->sheet))

(defn- road-object-sheets [ctx entity-id]
  (let [gml-geometry
        ;; PENDING: configure distance?
        (entity-features/entity-search-area-gml ctx entity-id 200)
        road-objects (road-query/fetch-all-intersecting-objects ctx gml-geometry)]
    (mapcat
     (fn [[type objects]]
       [(subs (name type) 3) ;; remove "ms:" from beginning
        (maps->sheet (map #(dissoc % :geometry) objects))])
     road-objects)))

(defquery :thk.project/download-related-info
  {:doc "Download restrictions and cadastral units data as Excel."
   :context {db :db}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]
   :authorization {:project/project-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}}
  ^{:format :raw}
  {:status 200
   :headers {"Content-Disposition" (str "attachment; filename=THK" id "-related.xlsx")}
   :body (let [ctx (environment/config-map {:api-url [:api-url]
                                            :api-secret [:auth :jwt-secret]
                                            :wfs-url [:road-registry :wfs-url]})
               {id :db/id :as project}
               (d/pull db '[:db/id
                            :thk.project/related-cadastral-units
                            :thk.project/related-restrictions]
                       [:thk.project/id id])

               related-cadastral-units (features/geojson-features-by-id
                                        ctx
                                        (:thk.project/related-cadastral-units project))
               related-restrictions (features/geojson-features-by-id
                                     ctx
                                     (:thk.project/related-restrictions project))

               road-object-sheets (road-object-sheets ctx id)]

           (ring-io/piped-input-stream
            (fn [out]
              (try
               (spreadsheet/save-workbook-into-stream!
                out
                (apply spreadsheet/create-workbook
                       "restrictions" (feature-collection->sheet-data related-restrictions)
                       "cadastral-units" (feature-collection->sheet-data related-cadastral-units)
                       road-object-sheets))
               (catch Throwable t
                 (log/error t "Failed to export Excel"))))))})


(defquery :thk.project/listing
  {:doc "List all project basic info"
   :context {db :db}
   :args _
   :project-id nil
   :authorization {}}
  (map
    project-model/project-with-status
    (meta-query/without-deleted
      db
      (mapv first
            (d/q '[:find (pull ?e columns)
                   :in $ columns
                   :where [?e :thk.project/id _]]
                 db project-model/project-list-with-status-attributes)))))

(defquery :thk.project/search
  {:doc "Search for a project by text"
   :context {db :db}
   :args {:keys [text]}
   :project-id nil
   :authorization {}}
  {:query '[:find (pull ?e [:db/id :thk.project/project-name :thk.project/name :thk.project/id])
            :where
            (or [?e :thk.project/project-name ?name]
                [?e :thk.project/name ?name])
            [(.toLowerCase ^String ?name) ?lower-name]
            [(.contains ?lower-name ?text)]
            :in $ ?text]
   :args [db (str/lower-case text)]
   :result-fn (partial mapv
                       #(-> % first (assoc :type :project)))})
