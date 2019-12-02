(ns teet.project.project-controller
  "Controller for project page"
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [teet.log :as log]
            [teet.project.project-model :as project-model]
            [teet.map.map-controller :as map-controller]
            goog.math.Long))


(defrecord OpenActivityDialog [])                              ; open add activity modal dialog
(defrecord OpenTaskDialog [])
(defrecord CloseAddDialog [])
(defrecord SelectProject [project-id])
(defrecord ToggleCadastralHightlight [id])
(defrecord ToggleRestrictionData [id])
(defrecord UpdateActivityState [id status])
(defrecord NavigateToProject [thk-project-id])

(defmethod common-controller/map-item-selected
  "geojson_entity_pins" [p]
  (->SelectProject (:map/id p)))

(defmethod common-controller/map-item-selected
  "mvt_entities" [p]
  (->SelectProject (:map/id p)))

(defmethod common-controller/map-item-selected
  "geojson_thk_project" [p]
  (->SelectProject (:map/id p)))

(defmethod common-controller/on-server-error :project-already-initialized [err {:keys [params page] :as app}]
  (t/fx (common-controller/default-server-error-handler err app)
        {:tuck.effect/type :navigate
         :page page
         :params params
         :query {}}
        common-controller/refresh-fx))

;;
;; Project setup wizard events
;;
(defrecord SaveBasicInformation [])
(defrecord SaveBasicInformationResponse [])
(defrecord UpdateBasicInformationForm [form-data])
(defrecord SaveRestrictions [])
(defrecord UpdateRestrictionsForm [form-data])
(defrecord SaveCadastralUnits [])
(defrecord UpdateCadastralUnitsForm [form-data])
(defrecord SaveActivities [])
(defrecord UpdateActivitiesForm [form-data])

(defrecord FetchRestrictions [])
(defrecord ToggleRestriction [id])

(defrecord PostActivityEditForm [])
(defrecord OpenEditActivityDialog [])
(defrecord InitializeActivityEditForm [])

(extend-protocol t/Event
  SaveBasicInformation
  (process-event [_ app]
    (let [{:thk.project/keys [id name] :as project} (get-in app [:route :project])
          {:thk.project/keys [project-name owner manager km-range meter-range-changed-reason]}
          (get-in app [:route :project :basic-information-form])
          [start-km end-km] km-range
          custom-km-range (mapv #(js/parseFloat %) km-range)]
      (t/fx app {:tuck.effect/type :command!
                 :command          :thk.project/initialize!
                 :payload          (merge {:thk.project/id id
                                           :thk.project/owner owner
                                           :thk.project/manager manager}
                                          (when (not= name project-name)
                                            {:thk.project/project-name project-name})
                                          (when (not= custom-km-range
                                                      (project-model/get-column project :thk.project/effective-km-range))
                                            {:thk.project/m-range-change-reason meter-range-changed-reason
                                             :thk.project/custom-start-m (long (* 1000 start-km))
                                             :thk.project/custom-end-m (long (* 1000 end-km))}))
                 :result-event     ->SaveBasicInformationResponse})))
  SaveBasicInformationResponse
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (assoc query :step "restrictions")}
          common-controller/refresh-fx))

  UpdateBasicInformationForm
  (process-event [{:keys [form-data]} app]
    (update-in app [:route :project :basic-information-form] merge form-data))

  FetchRestrictions
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :rpc
           :rpc "thk_project_related_restrictions"
           :endpoint (get-in app [:config :api-url])
           :args {:entity_id (str (get-in app [:route :project :db/id]))
                  :distance 200}
           :result-path [:route :project :restriction-candidates]}))

  ToggleRestriction
  (process-event [{id :id} app]
    (let [old-restrictions (or (get-in app [:route :project :checked-restrictions]) #{})
          new-restrictions (if (old-restrictions id)
                             (disj old-restrictions id)
                             (conj old-restrictions id))]
      (map-controller/update-features!
       "geojson_thk_project_related_restrictions"
       (fn [unit]
         (let [id (.get unit "id")]
           (.set unit "selected" (boolean (new-restrictions id))))))
      (assoc-in app [:route :project :checked-restrictions] new-restrictions))))

(extend-protocol t/Event
  SelectProject
  (process-event [{id :project-id} app]
    (t/fx app
          {:tuck.effect/type :query
           :query :thk.project/db-id->thk-id
           :args {:db/id (cond
                           (string? id)
                           (goog.math.Long/fromString id)

                           (number? id)
                           (goog.math.Long/fromNumber id))}
           :result-event ->NavigateToProject}))

  NavigateToProject
  (process-event [{id :thk-project-id} app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project id}}))

  OpenActivityDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
      {:tuck.effect/type :navigate
       :page page
       :params params
       :query (assoc query :add "activity")}))

  CloseAddDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :add :edit)}))

  OpenTaskDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
      {:tuck.effect/type :navigate
       :page page
       :params params
       :query (assoc query :add "task")}))

  PostActivityEditForm
  (process-event [_ {:keys [page params query edit-activity-data] :as app}]
    (let [activity-data edit-activity-data]
      (t/fx app
            {:tuck.effect/type :navigate
             :page page
             :query (dissoc query :edit)
             :params params}
            {:tuck.effect/type :command!
             :command          :project/update-activity
             :payload          activity-data
             :result-event     common-controller/->Refresh})))

  InitializeActivityEditForm
  (process-event [_ {:keys [query route] :as app}]
    (let [activity-data (project-model/activity-by-id (:project route) (:activity query))
          date-range [(:activity/estimated-start-date activity-data) (:activity/estimated-end-date activity-data)]
          activity (merge (select-keys activity-data [:activity/name :activity/status :db/id])
                          {:activity/status (get-in activity-data [:activity/status :db/ident])}
                          {:activity/name (get-in activity-data [:activity/name :db/ident])}
                          {:activity/estimated-date-range date-range})]
      (assoc app :edit-activity-data activity)))

  OpenEditActivityDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (assoc query :edit "activity")}))

  UpdateActivityState
  (process-event [{activity-id :id status :status} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :project/update-activity
           :payload          {:db/id           activity-id
                              :activity/status status}
           :result-event     common-controller/->Refresh
           :success-message  "Activity updated successfully"    ;TODO add to localizations
           }))

  ToggleRestrictionData
  (process-event [{restriction-id :id} app]
    (log/info "toggle restrictiondata " restriction-id)
    (map-controller/update-features!
      "geojson_thk_project_related_restrictions"
      (fn [unit]
        (let [id (.get unit "id")
              open? (.get unit "selected")]
          (when (= id restriction-id)
            (.set unit "selected" (not open?))))))
    (let [project-id (get-in app [:params :project])]
      (update-in app [:project project-id :restrictions]
        #(map
           (fn [e]
             (if (= (:id e) restriction-id)
               (assoc e :open? (not (:open? e)))
               e))
           %))))

  ToggleCadastralHightlight
  (process-event [{new-highlighted-id :id} app]
    (map-controller/update-features!
     "geojson_thk_project_related_cadastral_units"
     (fn [unit]
       (let [id (.get unit "id")
             open? (.get unit "selected")]
         (when (= id new-highlighted-id)
           (.set unit "selected" (not open?))))))
    (let [project-id (get-in app [:params :project])]
      (update-in app [:project project-id :cadastral-units]
        #(map
           (fn [e]
             (if (= (:id e) new-highlighted-id)
               (assoc e :open? (not (:open? e)))
               e))
           %)))))

(defn cadastral-units-rpc [project]
  {:rpc "thk_project_related_cadastral_units"
   :args {:entity_id (:db/id project)
          :distance 200}})

(defn restrictions-rpc [project]
  {:rpc "thk_project_related_restrictions"
   :args {:entity_id (:db/id project)
          :distance 200}})

(defmethod common-controller/map-item-selected
  "geojson_thk_project_related_cadastral_units"
  [p]
  ;(log/info "cadastral item selected: " p)
  (->ToggleCadastralHightlight (:map/id p)))

(defmethod common-controller/map-item-selected
  "geojson_thk_project_related_restrictions"
  [p]
  ;(log/info "cadastral item selected: " p)
  (->ToggleRestrictionData (:map/id p)))

(defn activity-url
  [{:keys [project lifecycle]} {id :db/id}]
  (str "#/projects/" project "/" lifecycle "/" id "?tab=details"))
