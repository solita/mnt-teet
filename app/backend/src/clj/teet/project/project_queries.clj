(ns teet.project.project-queries
  (:require [clojure.string :as str]
            [datomic.client.api :as d]
            [dk.ative.docjure.spreadsheet :as spreadsheet]
            [ring.util.io :as ring-io]
            [teet.activity.activity-db :as activity-db]
            [teet.db-api.core :as db-api :refer [defquery]]
            [teet.environment :as environment]
            [teet.gis.entity-features :as entity-features]
            [teet.gis.features :as features]
            [teet.log :as log]
            [teet.meta.meta-query :as meta-query]
            [teet.permission.permission-db :as permission-db]
            [teet.project.project-db :as project-db]
            [teet.project.project-model :as project-model]
            [teet.project.task-model :as task-model]
            [teet.road.road-model :as road-model]
            [teet.road.road-query :as road-query]
            [teet.task.task-db :as task-db]
            [teet.user.user-model :as user-model]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.integration.integration-id :as integration-id]
            [teet.file.file-db :as file-db]
            [teet.db-api.db-api-large-text :as db-api-large-text]
            [teet.comment.comment-db :as comment-db]
            [teet.contract.contract-db :as contract-db]))

(defquery :thk.project/integration-id->thk-id
  {:doc "Fetch THK project id for given entity :integration/id number"
   :context {db :db}
   :args {id :integration/id}
   :allowed-for-all-users? true}
  (:thk.project/id
   (du/entity db [:integration/id
                  (cond
                    (string? id)
                    (integration-id/number->uuid (BigInteger. id))

                    (number? id)
                    (integration-id/number->uuid id)

                    (uuid? id)
                    id

                    :else
                    (throw (ex-info "Unrecognized integration id"
                                    {:id id})))])))

(defn task-update
  [db user task-id task]
  (->> task
       (merge
         {:task/files (task-db/task-file-listing db user task-id)}
         (task-db/task-file-parts db task-id))
       (merge
         (comment-db/comment-count-of-entity-by-status db user task-id :task))))

(defn maybe-fetch-task-files [project db user task-id]
  (if-not task-id
    project
    (cu/update-path
     project
     [:thk.project/lifecycles some? ; all lifecycles
      :thk.lifecycle/activities some? ; all activities
      :activity/tasks #(= task-id (:db/id %))] ; matching task

     ;; Fetch and assoc the tasks

     (partial task-update db user task-id))))


(defn tasks-with-statuses
  [tasks]
  (map
    task-model/task-with-status
    tasks))

(defn- maybe-update-activity [fun project activity-id]
  (if-not activity-id
    project
    (cu/update-path
      project
      [:thk.project/lifecycles some?
       :thk.lifecycle/activities #(= activity-id (str (:db/id %)))]
      fun)))

(def maybe-update-activity-tasks
  (partial maybe-update-activity #(update % :activity/tasks tasks-with-statuses)))

(defn maybe-update-activity-has-files?
  [project db activity-id]
  (maybe-update-activity #(assoc % :activity-has-files? (file-db/activity-has-files? db (:db/id %)))
                         project activity-id))

(defn fetch-project [db user {:thk.project/keys [id]
                              task-id :task-id
                              activity-id :activity-id}]
  (let [project (meta-query/without-deleted
                 db
                 (project-db/project-by-id db [:thk.project/id id]))]
    (db-api-large-text/with-large-text
      #{:task/description}
      (-> project
          (assoc :thk.project/permitted-users
                 (project-model/users-with-permission
                  (permission-db/valid-project-permissions db (:db/id project))))
          (update :thk.project/lifecycles project-model/sort-lifecycles)
          (update :thk.project/lifecycles
                  (fn [lifecycle]
                    (map #(update % :thk.lifecycle/activities project-model/sort-activities) lifecycle)))
          (maybe-fetch-task-files db user task-id)
          (maybe-update-activity-tasks activity-id)
          (maybe-update-activity-has-files? db activity-id)
          (activity-db/update-activity-histories db)))))

(defquery :thk.project/fetch-project
  {:doc "Fetch project information"
   :context {:keys [db user]}
   :args {id :thk.project/id :as args}
   :project-id [:thk.project/id id]}
  (fetch-project db user args))

(defquery :thk.project/fetch-project-file
  {:doc "Fetch project info with task and files based on :file/id"
   :context {:keys [db user]}
   :args {id :file/id}
   :project-id (project-db/file-project-id
                db
                (file-db/file-by-uuid db id))}
  (let [{file-id :file :as navigation} (file-db/file-navigation-info-by-uuid db id)
        project-id (project-db/file-project-id db file-id)
        project-thk-id (:thk.project/id (du/entity db project-id))]
    (merge
     {:navigation navigation}
     (fetch-project db user {:thk.project/id project-thk-id
                             :task-id (get-in (du/entity db file-id)
                                              [:task/_files 0 :db/id])}))))

(defn- assignees-by-activity
  "Fetch all people who are assigned in some task in the given project.
  Returns assignees grouped by activity and includes their permissions
  for the project."
  [db project]
  (->> (d/q '[:find
              (pull ?act [:db/id :activity/name])
              (pull ?a [:db/id :user/given-name :user/family-name])
              :where
              [?project :thk.project/lifecycles ?lc]
              [?lc :thk.lifecycle/activities ?act]
              [(missing? $ ?act :meta/deleted?)]
              [?act :activity/tasks ?task]
              [(missing? $ ?task :meta/deleted?)]
              [?task :task/assignee ?a]
              :in
              $ ?project]
            db project)
       (group-by first)
       (cu/map-vals (partial mapv
                             (fn [[_ {user-id :db/id :as user}]]
                               (assoc user :permissions
                                      (mapv :permission/role
                                            (permission-db/user-permissions-in-project db user-id project))))))
       (cu/map-keys (comp :db/ident :activity/name))))

(defquery :thk.project/assignees-by-activity
  {:doc "Fetch assignees by activity"
   :context {db :db}
   :args {:thk.project/keys [id]}
   :project-id [:thk.project/id id]}
  (assignees-by-activity db [:thk.project/id id]))

(defn- maps->sheet [maps]
  (let [headers (sort (reduce into #{}
                              (map keys maps)))]
    (into [(mapv name headers)]
          (map (apply juxt headers))
          maps)))

(defn- feature-collection->sheet-data [feature-collection]
  (when (:features feature-collection)
    (->> feature-collection
         :features
         (map :properties)
         maps->sheet)))

(defn- road-object-sheets [ctx integration-id]
  (let [gml-geometry
        (entity-features/entity-search-area-gml ctx integration-id
                                                road-model/default-road-buffer-meters)
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
   :project-id [:thk.project/id id]}
  ^{:format :raw}
  {:status 200
   :headers {"Content-Disposition" (str "attachment; filename=THK" id "-related.xlsx")}
   :body (let [ctx (environment/config-map {:api-url [:api-url]
                                            :api-secret [:auth :jwt-secret]
                                            :wfs-url [:road-registry :wfs-url]})
               {integration-id :integration/id :as project}
               (d/pull db '[:db/id :integration/id
                            :thk.project/related-cadastral-units
                            :thk.project/related-restrictions]
                       [:thk.project/id id])

               related-cadastral-units (features/geojson-features-by-id
                                        ctx
                                        (:thk.project/related-cadastral-units project))
               related-restrictions (features/geojson-features-by-id
                                     ctx
                                     (:thk.project/related-restrictions project))

               road-object-sheets (road-object-sheets ctx integration-id)]

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
   :context {db :db
             user :user}
   :args _
   :spec empty?
   :allowed-for-all-users? true}
  {:projects-with-contract-access (contract-db/projects-the-user-has-access-through-contracts db (:db/id user))
   :projects (map
               project-model/project-with-status
               (meta-query/without-deleted
                 db
                 (mapv first
                       (d/q '[:find (pull ?e columns)
                              :in $ columns
                              :where [?e :thk.project/id _]]
                            db project-model/project-list-with-status-attributes))))})

(defmulti search-clause (fn [[field _value]] field))

(defmethod search-clause :text [[_ text]]
  (if (every? #(Character/isDigit %) text)
    ;; Search by object id
    {:where '[[?e :thk.project/id ?thk-project-id]]
     :in {'?thk-project-id text}}
    ;; Search by free text
    {:where '[(or [?e :thk.project/project-name ?name]
                  [?e :thk.project/name ?name])
              [(.toLowerCase ^String ?name) ?lower-name]
              [(.contains ?lower-name ?text)]]
     :in {'?text (str/lower-case text)}}))

(defmethod search-clause :road [[_ road]]
  {:where '[[?e :thk.project/road-nr ?road]]
   :in {'?road (Long/parseLong road)}})

(defmethod search-clause :km [[_ km]]
  {:where '[(or [?e :thk.project/custom-start-m ?start-m]
                [?e :thk.project/start-m ?start-m])
            [(<= ?start-m ?meters)]
            (or [?e :thk.project/custom-end-m ?end-m]
                [?e :thk.project/end-m ?end-m])
            [(>= ?end-m ?meters)]]
   :in {'?meters (-> km road-model/parse-km road-model/km->m)}})

(defmethod search-clause :region [[_ region]]
  {:where '[[?e :thk.project/region-name ?region-name]
            [(.toLowerCase ^String ?region-name) ?lower-region-name]
            [(.contains ?lower-region-name ?region)]]
   :in {'?region (str/lower-case region)}})

(defmethod search-clause :date [[_ date]]
  {:where '[[?e :thk.project/estimated-start-date ?start-date]
            [(<= ?start-date ?date)]
            [?e :thk.project/estimated-end-date ?end-date]
            [(>= ?end-date ?date)]]
   :in {'?date date}})

(defmethod search-clause :repair-method [[_ repair-method]]
  {:where '[[?e :thk.project/repair-method ?repair-method]]
   :in {'?repair-method repair-method}})

(defmethod search-clause :owner [[_ owner]]
  {:where '[[?e :thk.project/owner ?owner]]
   :in {'?owner (user-model/user-ref owner)}})

(defmethod search-clause :status [[_ _status]]
  ;; FIXME: status?
  {:where []
   :in {}})

(defquery :thk.project/search
  {:doc "Search for a projects"
   :context {db :db}
   :args payload
   :allowed-for-all-users? true}
  (let [{:keys [where in]}
        (reduce (fn [clauses-and-args search]
                  (let [{:keys [where in]} (search-clause search)]
                    (-> clauses-and-args
                        (update :where concat where)
                        (update :in merge in))))
                {:where []
                 :in {}}
                payload)

        arglist (seq in)
        in (into '[$] (map first) arglist)
        args (into [db] (map second) arglist)]
    (mapv #(-> % first
               (assoc :type :project)
               (update :integration/id (comp str integration-id/uuid->number)))
          (d/q (let [q {:query {:find '[(pull ?e [:db/id :integration/id
                                                  :thk.project/project-name
                                                  :thk.project/name
                                                  :thk.project/id])]
                               :where (into '[[?e :thk.project/id _]] where)
                               :in in}
                        :args args}]
                 (log/info "Q: " q)
                 q)))))
