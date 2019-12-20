(ns teet.project.project-controller
  "Controller for project page"
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [teet.log :as log]
            [teet.project.project-model :as project-model]
            [teet.road.road-model :as road-model]
            [teet.map.map-controller :as map-controller]
            goog.math.Long
            [clojure.string :as str]))


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

(defn project-setup-step [app]
  (-> app :route :project :setup-step))

(defn- navigate-to-step [app step]
  (assoc-in app [:route :project :setup-step] step))

(defrecord NavigateToStep [step])

(defrecord SaveProjectSetup [])
(defrecord SaveProjectSetupResponse [])
(defrecord UpdateBasicInformationForm [form-data])
(defrecord ChangeRoadObjectAoe [val])
(defrecord SaveRestrictions [])
(defrecord UpdateRestrictionsForm [form-data])
(defrecord SaveCadastralUnits [])
(defrecord UpdateCadastralUnitsForm [form-data])
(defrecord SaveActivities [])
(defrecord UpdateActivitiesForm [form-data])

(defrecord FetchRestrictions [road-buffer-meters])
(defrecord ToggleRestriction [id])

(defrecord PostActivityEditForm [])
(defrecord OpenEditActivityDialog [])
(defrecord InitializeActivityEditForm [])

(defrecord DeleteActivity [activity-id])
(defrecord DeleteActivityResult [response])

(defrecord FetchRelatedFeaturesResponse [result-path geojson-path response])

(defn fetch-related-info
  [app road-buffer-meters]
  (let [args {:entity_id (str (get-in app [:route :project :db/id]))
              :distance road-buffer-meters}]
    (merge
     {:tuck.effect/type :rpc
      :rpc "geojson_entity_related_features"
      :endpoint (get-in app [:config :api-url])}
     (case (project-setup-step app)
       "restrictions"
       {:args (assoc args
                     ;; FIXME: dataosource ids from map datasources info
                     :datasource_ids (map-controller/select-rpc-datasources
                                      app map-controller/restriction-datasource?))
        :result-event (partial ->FetchRelatedFeaturesResponse
                               [:route :project :restriction-candidates]
                               [:route :project :restriction-candidates-geojson])}

       "cadastral-units"
       {:args (assoc args
                     :datasource_ids (map-controller/select-rpc-datasources
                                      app map-controller/cadastral-unit-datasource?))
        :result-event (partial ->FetchRelatedFeaturesResponse
                               [:route :project :cadastral-candidates]
                               [:route :project :cadastral-candidates-geojson])}
       {}))))

(defn navigate-to-next-step-event
  "Given `current-step`, navigates to next step in `steps`"
  [steps {:keys [step-number] :as _current-step}]
  {:pre [(<= step-number (count steps))]}
  (if (= step-number (count steps))
    ;; At the last step, return save event
    ->SaveProjectSetup

    ;; Otherwise navigate to next step
    (let [step-label (-> (get steps step-number) :step-label name)]
      (fn []
        (->NavigateToStep step-label)))))

(defn navigate-to-previous-step-event
  "Given `current-step`, navigatest to next step in `steps`"
  [steps {:keys [step-number] :as _current-step}]
  {:pre [(> step-number 1)]}
  (let [step-label (-> (get steps (- step-number 2)) :step-label name)]
    (fn []
      (->NavigateToStep step-label))))

(extend-protocol t/Event
  FetchRelatedFeaturesResponse
  (process-event [{:keys [result-path geojson-path response]} app]
    (let [geojson (js/JSON.parse response)
          features (-> geojson
                       (js->clj :keywordize-keys true)
                       :features
                       (as-> fs
                           (map :properties fs)))]
      (-> app
          (assoc-in result-path features)
          (assoc-in geojson-path geojson))))

  ChangeRoadObjectAoe
  (process-event [{val :val} {:keys [page params] :as app}]
    (let [app (assoc-in app [:map :road-buffer-meters] val)]
      (if (and (project-setup-step app) (not-empty val))
        (t/fx app
              {:tuck.effect/type :debounce
               :timeout          300
               :effect           (fetch-related-info app val)})
        app)))

  DeleteActivity
  (process-event [{activity-id :activity-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :project/delete-activity
           :success-message  "Activity deletion success"    ;;TODO add localization
           :payload          {:db/id (goog.math.Long/fromString activity-id)}
           :result-event     ->DeleteActivityResult}))

  DeleteActivityResult
  (process-event [{response :response} {:keys [params query page] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (dissoc query :activity :edit)}
          common-controller/refresh-fx))

  NavigateToStep
  (process-event [{step :step} app]
    (navigate-to-step app step))

  SaveProjectSetup
  (process-event [_ app]
    (let [{:thk.project/keys [id name] :as project} (get-in app [:route :project])
          {:thk.project/keys [project-name owner manager km-range meter-range-changed-reason]}
          (get-in app [:route :project :basic-information-form])
          [start-km end-km :as custom-km-range] (mapv road-model/parse-km km-range)
          checked-restrictions (not-empty (get-in app [:route :project :checked-restrictions]))]
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
                                             :thk.project/custom-start-m (road-model/km->m start-km)
                                             :thk.project/custom-end-m (road-model/km->m end-km)})
                                          (when checked-restrictions
                                            {:thk.project/related-restrictions checked-restrictions}))
                 :result-event     common-controller/->Refresh})))

  UpdateBasicInformationForm
  (process-event [{:keys [form-data]} app]
    (let [{:thk.project/keys [road-nr carriageway]} (get-in app [:route :project])
          actual-road-info (get-in app [:route :project :basic-information-form :road-info])]
      (if actual-road-info
        (update-in app [:route :project :basic-information-form]
                   merge form-data)
        (t/fx (update-in app [:route :project :basic-information-form]
                         merge form-data)
              {:tuck.effect/type :rpc
               :endpoint         (get-in app [:config :api-url])
               :method           :GET
               :rpc              "road_info"
               :args             {:road        road-nr
                                  :carriageway carriageway}
               :result-path      [:route :project :basic-information-form :road-info]}))))

  FetchRestrictions
  (process-event [{road-buffer-meters :road-buffer-meters} app]
    (t/fx app
          (fetch-related-info app road-buffer-meters)))

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
       :params {:project id}}
      ))

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
