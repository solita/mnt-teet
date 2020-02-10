(ns teet.project.project-controller
  "Controller for project page"
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [teet.log :as log]
            [cljs-bean.core :refer [->clj ->js]]
            [teet.project.project-model :as project-model]
            [teet.localization :refer [tr]]
            [teet.road.road-model :as road-model]
            [teet.map.map-controller :as map-controller]
            goog.math.Long
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defrecord OpenActivityDialog [lifecycle])                  ; open add activity modal dialog
(defrecord OpenTaskDialog [activity])
(defrecord OpenEditProjectDialog [])
(defrecord PostProjectEdit [])
(defrecord PostProjectEditResult [])
(defrecord OpenPeopleModal [])
(defrecord UpdateProjectPermissionForm [form-data])
(defrecord SaveProjectPermission [project-id form-data])
(defrecord RevokeProjectPermission [permission-id])
(defrecord RevokeProjectPermissionSuccess [result])

(defrecord CloseDialog [])
(defrecord SelectProject [project-id])
(defrecord SelectCadastralUnit [p])
(defrecord SelectRestriction [p])

(defrecord ToggleRestrictionData [id])
(defrecord UpdateActivityState [id status])
(defrecord NavigateToProject [thk-project-id])

(defmethod common-controller/on-server-error :permission-already-granted [err app]
  (let [error (-> err ex-data :error)]
    (t/fx (snackbar-controller/open-snack-bar app (tr [:error error]) :warning)
          common-controller/refresh-fx)))

(defmethod common-controller/map-item-selected
  "geojson_entity_pins" [p]
  (->SelectProject (:map/id p)))

(defmethod common-controller/map-item-selected
  "mvt_entities" [p]
  (->SelectProject (:map/id p)))

(defmethod common-controller/map-item-selected
  "geojson_thk_project" [p]
  (->SelectProject (:map/id p)))

(defmethod common-controller/map-item-selected
  "related-cadastral-unit-candidates" [p]
  (->SelectCadastralUnit p))

(defmethod common-controller/map-item-selected
  "selected-cadastral-units" [p]
  (->SelectCadastralUnit p))

(defmethod common-controller/map-item-selected
  "selected-restrictions" [p]
  (->SelectRestriction p))

(defmethod common-controller/map-item-selected
  "related-restriction-candidates" [p]
  (->SelectRestriction p))

(defmethod common-controller/on-server-error :project-already-initialized [err {:keys [params page] :as app}]
  (t/fx (common-controller/default-server-error-handler err app)
        {:tuck.effect/type :navigate
         :page             page
         :params           params
         :query            {}}
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
(defrecord ToggleRestriction [restriction])
(defrecord ToggleCadastralUnit [cadastral-unit])
(defrecord FeatureMouseOvers [layer enter? feature])
(defrecord ToggleRestrictionCategory [restrictions group])
(defrecord ToggleSelectedCategory [])
(defrecord ToggleStepperLifecycle [lifecycle])
(defrecord ToggleStepperActivity [activity])

(defrecord PostActivityEditForm [])
(defrecord OpenEditActivityDialog [activity-id lifecycle-id])
(defrecord InitializeActivityEditForm [])
(defrecord ContinueProjectSetup [project-id])
(defrecord SkipProjectSetup [project-id])

(defrecord DeleteActivity [activity-id])
(defrecord DeleteActivityResult [response])

(defrecord FetchRelatedFeaturesResponse [result-path geojson-path response])

(defn fetch-related-info
  [app road-buffer-meters]
  (let [args {:entity_id (str (get-in app [:route :project :db/id]))
              :distance  road-buffer-meters}]
    (merge
      {:tuck.effect/type :rpc
       :rpc              "geojson_entity_related_features"
       :endpoint         (get-in app [:config :api-url])}
      (case (project-setup-step app)
        "restrictions"
        {:args         (assoc args
                         ;; FIXME: dataosource ids from map datasources info
                         :datasource_ids (map-controller/select-rpc-datasources
                                           app map-controller/restriction-datasource?))
         :result-event (partial ->FetchRelatedFeaturesResponse
                                [:route :project :restriction-candidates]
                                [:route :project :restriction-candidates-geojson])}

        "cadastral-units"
        {:args         (assoc args
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

(defn toggle-cadastral-unit
  [app cadastral-unit]
  (let [old-cadastral-units (or (get-in app [:route :project :checked-cadastral-units])
                                #{})
        new-cadastral-units (if (old-cadastral-units cadastral-unit)
                              (disj old-cadastral-units cadastral-unit)
                              (conj old-cadastral-units cadastral-unit))
        cadastral-candidates-geojson (get-in app [:route :project :cadastral-candidates-geojson])
        candidates-match (->> cadastral-candidates-geojson
                              ->clj
                              :features
                              (filter #(= (get-in % [:properties :teet-id]) (:teet-id cadastral-unit)))
                              first)
        checked-match (first
                        (filter
                          #(= (get-in % [:properties :teet-id]) (:teet-id cadastral-unit))
                          (get-in app [:route :project :checked-cadastral-geojson])))
        cadastral-geojson (or checked-match candidates-match)
        old-cadastral-geojson (or (get-in app [:route :project :checked-cadastral-geojson])
                                  #{})
        new-cadastral-geojson (if (old-cadastral-geojson cadastral-geojson)
                                (disj old-cadastral-geojson cadastral-geojson)
                                (conj old-cadastral-geojson cadastral-geojson))]
    (-> app
        (assoc-in [:route :project :checked-cadastral-geojson] new-cadastral-geojson)
        (assoc-in [:route :project :checked-cadastral-units] new-cadastral-units))))

(defn toggle-restriction
  [app restriction]
  (let [old-restrictions (or (get-in app [:route :project :checked-restrictions])
                             #{})
        new-restrictions (if (old-restrictions restriction)
                           (disj old-restrictions restriction)
                           (conj old-restrictions restriction))
        restriction-candidates-geojson (get-in app [:route :project :restriction-candidates-geojson])
        candidates-match (->> restriction-candidates-geojson
                              ->clj
                              :features
                              (filter #(= (get-in % [:properties :teet-id]) (:teet-id restriction)))
                              first)
        checked-match (first
                        (filter
                          #(= (get-in % [:properties :teet-id]) (:teet-id restriction))
                          (get-in app [:route :project :checked-restrictions-geojson])))

        restriction-geojson (or checked-match candidates-match)
        old-restrictions-geojson (or (get-in app [:route :project :checked-restrictions-geojson])
                                     #{})
        new-restrictions-geojson (if (old-restrictions-geojson restriction-geojson)
                                   (disj old-restrictions-geojson restriction-geojson)
                                   (conj old-restrictions-geojson restriction-geojson))]

    (-> app
        (assoc-in [:route :project :checked-restrictions] new-restrictions)
        (assoc-in [:route :project :checked-restrictions-geojson] new-restrictions-geojson))))

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

  SelectCadastralUnit
  (process-event [{p :p} app]
    (let [cadastral-candidates (get-in app [:route :project :cadastral-candidates])
          cadastral-selections (get-in app [:route :project :checked-cadastral-units])
          cadastral-unit (or
                           (first (filter #(= (:teet-id %) (:map/teet-id p)) cadastral-selections))
                           (first (filter #(= (:teet-id %) (:map/teet-id p)) cadastral-candidates)))]
      (toggle-cadastral-unit app cadastral-unit)))

  SelectRestriction
  (process-event [{p :p} app]
    (let [restriction-candidates (get-in app [:route :project :restriction-candidates])
          restriction-selections (get-in app [:route :project :checked-restrictions])
          restriction (or
                        (first (filter #(= (:teet-id %) (:map/teet-id p)) restriction-selections))
                        (first (filter #(= (:teet-id %) (:map/teet-id p)) restriction-candidates)))]
      (toggle-restriction app restriction)))

  ContinueProjectSetup
  (process-event [{project-id :project-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :thk.project/continue-setup
           :payload          {:thk.project/id project-id}
           :result-event     common-controller/->Refresh}))

  SkipProjectSetup
  (process-event [{project-id :project-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :thk.project/skip-setup
           :payload          {:thk.project/id project-id}
           :result-event     common-controller/->Refresh}))

  ChangeRoadObjectAoe
  (process-event [{val :val} {:keys [page params] :as app}]
    (let [app (assoc-in app [:map :road-buffer-meters] val)]
      (if (and (project-setup-step app) (not-empty val))
        (t/fx app
              {:tuck.effect/type :debounce
               :timeout          600
               :effect           (fetch-related-info app val)})
        app)))

  DeleteActivity
  (process-event [{activity-id :activity-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :activity/delete
           :success-message  (tr [:notifications :activity-deleted])
           :payload          {:db/id (goog.math.Long/fromString activity-id)}
           :result-event     ->DeleteActivityResult}))

  DeleteActivityResult
  (process-event [{response :response} {:keys [params query page] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :activity :edit)}
          common-controller/refresh-fx))

  NavigateToStep
  (process-event [{step :step} app]
    (navigate-to-step app step))

  PostProjectEdit
  (process-event [_ app]
    (let [{:thk.project/keys [id]} (get-in app [:route :project])
          {:thk.project/keys [project-name owner manager]}
          (get-in app [:route :project :basic-information-form])]
      (t/fx app {:tuck.effect/type :command!
                 :command          :thk.project/edit-project
                 :payload          {:thk.project/id           id
                                    :thk.project/owner        owner
                                    :thk.project/manager      manager
                                    :thk.project/project-name project-name}
                 :result-event     ->PostProjectEditResult})))

  PostProjectEditResult
  (process-event [_ {:keys [params query page] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :edit)}
          common-controller/refresh-fx))

  SaveProjectSetup
  (process-event [_ app]
    (let [{:thk.project/keys [id name] :as project} (get-in app [:route :project])
          {:thk.project/keys [project-name owner manager km-range meter-range-changed-reason]}
          (get-in app [:route :project :basic-information-form])
          [start-km end-km :as custom-km-range] (mapv road-model/parse-km km-range)
          checked-restrictions (not-empty (get-in app [:route :project :checked-restrictions]))
          checked-cadastral-units (not-empty (get-in app [:route :project :checked-cadastral-units]))]
      (t/fx app {:tuck.effect/type :command!
                 :command          :thk.project/initialize!
                 :payload          (merge {:thk.project/id      id
                                           :thk.project/owner   owner
                                           :thk.project/manager manager}
                                          (when (not= name project-name)
                                            {:thk.project/project-name project-name})
                                          (when (not= custom-km-range
                                                      (project-model/get-column project :thk.project/effective-km-range))
                                            {:thk.project/m-range-change-reason meter-range-changed-reason
                                             :thk.project/custom-start-m        (road-model/km->m start-km)
                                             :thk.project/custom-end-m          (road-model/km->m end-km)})
                                          (when checked-restrictions
                                            {:thk.project/related-restrictions (map :teet-id
                                                                                    checked-restrictions)})
                                          (when checked-cadastral-units
                                            {:thk.project/related-cadastral-units (map :teet-id
                                                                                       checked-cadastral-units)}))
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
  (process-event [{restriction :restriction} app]
    (toggle-restriction app restriction))

  ToggleCadastralUnit
  (process-event [{cadastral-unit :cadastral-unit} app]
    (toggle-cadastral-unit app cadastral-unit))

  ToggleSelectedCategory
  (process-event [{} app]
    (let [open-types (or (get-in app [:route :project :open-types])
                         #{})
          new-open-types (if (open-types :selected)
                           (disj open-types :selected)
                           (conj open-types :selected))]
      (assoc-in app [:route :project :open-types] new-open-types)))

  ToggleRestrictionCategory
  (process-event [{restrictictions :restrictions
                   group           :group}
                  app]
    ;;Given a set of restriction ids and whether they are being opened, select a set of geojsons to show on map.
    (let [open-types (or (get-in app [:route :project :open-types])
                         #{})
          opening? (not (open-types group))
          new-open-types (if opening?
                           (conj open-types group)
                           (disj open-types group))
          restriction-geojsons (get-in app [:route :project :restriction-candidates-geojson])
          previously-open-geojsons (-> (get-in app [:route :project :open-restrictions-geojsons])
                                       ->clj
                                       :features)
          new-matches (->> restriction-geojsons
                           ->clj
                           :features
                           (filter #(restrictictions (get-in % [:properties :teet-id])))
                           (concat previously-open-geojsons))
          geojsons-without-matches (->> previously-open-geojsons
                                        (filter #(not (restrictictions (get-in % [:properties :teet-id])))))
          new-geojsons (->> (if opening?
                              new-matches
                              geojsons-without-matches)
                            (into [])
                            (assoc {"type" "FeatureCollection"} "features")
                            ->js)]
      (-> app
          (assoc-in [:route :project :open-types] new-open-types)
          (assoc-in [:route :project :open-restrictions-geojsons] new-geojsons))))

  FeatureMouseOvers
  (process-event [{layer   :layer
                   enter?  :enter?
                   feature :feature}
                  app]
    (map-controller/update-features!
      layer
      (fn [unit]
        (let [id (.get unit "teet-id")]
          (if (and (= id (:teet-id feature)) enter?)
            (.set unit "hover" enter?)
            (.set unit "hover" false)))))
    app))


(extend-protocol t/Event
  SelectProject
  (process-event [{id :project-id} app]
    (t/fx app
          {:tuck.effect/type :query
           :query            :thk.project/db-id->thk-id
           :args             {:db/id (cond
                                       (string? id)
                                       (goog.math.Long/fromString id)

                                       (number? id)
                                       (goog.math.Long/fromNumber id))}
           :result-event     ->NavigateToProject}))

  NavigateToProject
  (process-event [{id :thk-project-id} app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             :project
           :params           {:project id}}))

  OpenActivityDialog
  (process-event [{lifecycle :lifecycle} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :add "activity"
                                          :lifecycle lifecycle)}))

  OpenPeopleModal
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :modal "people")}))

  SaveProjectPermission
  (process-event [{project-id :project-id form-data :form-data} app]
    (let [participant (:project/participant form-data)]
      (t/fx app
            {:tuck.effect/type :command!
             :command          :project/add-permission
             :payload          {:project-id  project-id
                                :user participant}
             :success-message  (tr [:notifications :permission-added-successfully])
             :result-event     common-controller/->Refresh})))

  RevokeProjectPermission
  (process-event [{permission-id :permission-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :project/revoke-permission
           :payload          {:permission-id permission-id}
           :success-message  (tr [:notifications :permission-revoked])
           :result-event     ->RevokeProjectPermissionSuccess}))

  RevokeProjectPermissionSuccess
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :person)}
          common-controller/refresh-fx))

  UpdateProjectPermissionForm
  (process-event [{form-data :form-data} app]
    (assoc-in app [:route :project :add-participant] form-data))


  CloseDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :modal :add :edit :activity :lifecycle)}))

  OpenTaskDialog
  (process-event [{activity :activity} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :add "task"
                                          :activity activity)}))

  PostActivityEditForm
  (process-event [_ {:keys [page params query edit-activity-data] :as app}]
    (let [activity-data edit-activity-data]
      (t/fx app
            {:tuck.effect/type :navigate
             :page             page
             :query            (dissoc query :edit)
             :params           params}
            {:tuck.effect/type :command!
             :command          :activity/update
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

  OpenEditProjectDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :edit "project")}))

  OpenEditActivityDialog
  (process-event [{activity-id :activity-id
                   lifecycle-id :lifecycle-id} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :edit "activity"
                                          :activity activity-id
                                          :lifecycle lifecycle-id)}))

  UpdateActivityState
  (process-event [{activity-id :id status :status} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :activity/update
           :payload          {:db/id           activity-id
                              :activity/status status}
           :result-event     common-controller/->Refresh
           :success-message  (tr [:notifications :activity-updated])}))


  ToggleStepperLifecycle
  (process-event [{lifecycle :lifecycle} app]
    (if (= (str lifecycle) (str (get-in app [:stepper :lifecycle])))
      (assoc-in app [:stepper :lifecycle] nil)
      (assoc-in app [:stepper :lifecycle] lifecycle)))

  ToggleStepperActivity
  (process-event [{activity :activity} app]
    (if (= (str activity) (str (get-in app [:stepper :activity])))
      (assoc-in app [:stepper :activity] nil)
      (assoc-in app [:stepper :activity] activity)))

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
                    %)))))


(defn activity-url
  [{:keys [project lifecycle]} {id :db/id}]
  (str "#/projects/" project "/" lifecycle "/" id "?tab=details"))
