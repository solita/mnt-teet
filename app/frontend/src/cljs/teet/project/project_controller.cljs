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
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [clojure.string :as str]
            [clojure.set :as set]
            [teet.map.openlayers :as openlayers]
            [teet.util.collection :as cu]))

(defrecord OpenActivityDialog [lifecycle])                  ; open add activity modal dialog
(defrecord OpenEditProjectDialog [])
(defrecord OpenEditDetailsDialog [])
(defrecord PostProjectEdit [on-result-callback])
(defrecord PostProjectEditResult [on-result-callback result])
(defrecord SaveProjectOwner [on-result-callback owner])
(defrecord SaveProjectOwnerResult [on-result-callback result])
(defrecord OpenPeopleModal [])
(defrecord UpdateProjectPermissionForm [form-data])
(defrecord SaveProjectPermission [project-id form-data reset-form-value-event])
(defrecord SaveProjectPermissionResult [reset-form-value-event response])
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

(defrecord InitializeBasicInformationForm [form-data])
(defrecord UpdateBasicInformationForm [form-data])
(defrecord ProjectGeometryFetchSuccess [result])
(defrecord CancelUpdateProjectInformation [])
(defrecord UpdateProjectRestrictions [restrictions project-id])
(defrecord UpdateProjectCadastralUnits [cadastral-units project-id])
(defrecord FeaturesUpdatedSuccessfully [result])
(defrecord RoadGeometryAndInfoResponse [result])

(defrecord FetchRelatedCandidates [road-buffer-meters entity-type])
(defrecord FetchRelatedFeatures [feature-ids feature-type])
(defrecord RelatedFeaturesSuccess [type result])
(defrecord ToggleRestriction [restriction])
(defrecord SelectRestrictions [restrictions])
(defrecord DeselectRestrictions [restrictions])
(defrecord ToggleCadastralUnit [cadastral-unit])
(defrecord SelectCadastralUnits [cadastral-units])
(defrecord DeselectCadastralUnits [cadastral-units])

(defrecord FeatureMouseOvers [layer enter? feature])
(defrecord ToggleRestrictionCategory [restrictions group])
(defrecord ToggleSelectedCategory [])
(defrecord ToggleStepperLifecycle [lifecycle])
(defrecord ToggleStepperActivity [activity])

(defrecord DrawSelectionOnMap [related-feature-type features]) ; start drawing selection on map
(defrecord DrawSelectionDone [area related-feature-type features]) ; selection drawn
(defrecord DrawSelectionResults [geojson related-feature-type features])
(defrecord DrawSelectionConfirm [select? related-feature-type])
(defrecord DrawSelectionCancel [])


(defrecord OpenEditActivityDialog [activity-id])

(defrecord DeleteActivity [activity-id])
(defrecord DeleteActivityResult [response])

(defrecord FetchFeatureCandidatesResponse [candidate-type response])

(defmulti app->project
  "Get project map from app state.
  Currently the default implementation works for all /projects/* pages, but this can be overridden
  for new pages if needed."
  (fn [app] (:page app)))

(defmethod app->project :default [app]
  (or (and (common-controller/page-state app :thk.project/id)
           (common-controller/page-state app))
      (common-controller/page-state app :project)))

(defn datasource-ids-by-type
  [app type]
  (case type
    (:restrictions "restrictions")
    (map-controller/select-rpc-datasources
      app map-controller/restriction-datasource?)

    (:cadastral-units "cadastral-units")
    (map-controller/select-rpc-datasources
      app map-controller/cadastral-unit-datasource?)))

(defn fetch-related-candidates
  [app road-buffer-meters info-type]
  (let [entity-id (str (get-in app [:route :project :integration/id]))
        [rpc args]
        (if (= (get-in app [:map :search-area :tab]) :drawn-area)
          ["geojson_related_features_for_entity_by_type"
           {:entity_id entity-id
            :type "search-area"}]
          (if-let [g (get-in app [:route :project :geometry])]
            ;; If we have a project geometry (in setup wizard)
            ;; fetch features by giving the geometry area
            ["geojson_features_within_area"
             {:geometry_wkt (str "LINESTRING("
                                 (str/join "," (map #(str/join " " %) g))
                                 ")")
              :distance road-buffer-meters}]

            ;; Otherwise get related features based on the stored entity geometry
            ["geojson_entity_related_features"
             {:entity_id entity-id
              :distance road-buffer-meters}]))]
    {:tuck.effect/type :rpc
     :rpc rpc
     :loading-path [:route :project :feature-candidates]
     :endpoint (get-in app [:config :api-url])
     :args (assoc args :datasource_ids
                       (datasource-ids-by-type app info-type))
     :result-event
     (case info-type
       "restrictions"
       (partial ->FetchFeatureCandidatesResponse :restrictions)
       "cadastral-units"
       (partial ->FetchFeatureCandidatesResponse :cadastral-units))}))

(defn- update-related-features [features-path features-geojson-path feature-candidates-geojson-path
                                app new-features]
  (let [ids (into #{} (map :teet-id) new-features)
        selected-features-geojson (get-in app features-geojson-path)
        feature-candidates-geojson (->> (get-in app feature-candidates-geojson-path)
                                        ->clj
                                        :features)
        new-features-geojson (into #{}
                                   (filter #(ids (get-in % [:properties :teet-id])))
                                   (concat selected-features-geojson feature-candidates-geojson))]
    (-> app
        (assoc-in features-path new-features)
        (assoc-in features-geojson-path new-features-geojson))))

(def update-checked-restrictions (partial update-related-features
                                          [:route :project :checked-restrictions]
                                          [:route :project :checked-restrictions-geojson]
                                          [:route :project :feature-candidates :restriction-candidates-geojson]))

(def update-checked-cadastral-units (partial update-related-features
                                             [:route :project :checked-cadastral-units]
                                             [:route :project :checked-cadastral-geojson]
                                             [:route :project :feature-candidates :cadastral-candidates-geojson]))

(defn toggle-related-feature [features-path update-checked-fn app feature]
  (update-checked-fn
   app
   (let [old-features (get-in app features-path #{})]
     (if (old-features feature)
       (disj old-features feature)
       (conj old-features feature)))))

(def toggle-cadastral-unit (partial toggle-related-feature
                                    [:route :project :checked-cadastral-units]
                                    update-checked-cadastral-units))

(def toggle-restriction (partial toggle-related-feature
                                 [:route :project :checked-restrictions]
                                 update-checked-restrictions))

(defn navigate-to-previous-step-event
  "Given `current-step`, navigatest to next step in `steps`"
  [steps {:keys [step-number] :as _current-step}]
  {:pre [(> step-number 1)]}
  (let [step-label (-> (get steps (- step-number 2)) :step-label name)]
    (fn []
      (->NavigateToStep step-label))))

(def candidate-paths
  {:restrictions {:result-path [:route :project :feature-candidates :restriction-candidates]
                  :geojson-path [:route :project :feature-candidates :restriction-candidates-geojson]
                  :selected-feature-path [:route :project :thk.project/related-restrictions]
                  :checked-feature-path [:route :project :checked-restrictions]
                  :checked-feature-geojson-path [:route :project :checked-restrictions-geojson]}
   :cadastral-units {:result-path [:route :project :feature-candidates :cadastral-candidates]
                     :geojson-path [:route :project :feature-candidates :cadastral-candidates-geojson]
                     :selected-feature-path [:route :project :thk.project/related-cadastral-units]
                     :checked-feature-path [:route :project :checked-cadastral-units]
                     :checked-feature-geojson-path [:route :project :checked-cadastral-geojson]}})

(defn- task->edit-task-data
  "updates task data obtained from the db into one suitable for task form"
  [task]
  (-> task
      (update :task/group :db/ident)
      (update :task/status :db/ident)
      (update :task/assignee #(when %
                                (select-keys % [:user/email :user/family-name :user/given-name :user/id])))))

(extend-protocol t/Event
  DrawSelectionOnMap
  (process-event [{:keys [related-feature-type features]} app]
    (openlayers/enable-draw!
     (t/send-async! ->DrawSelectionDone related-feature-type features))
    (assoc-in app [:map :search-area :drawing?] true))

  DrawSelectionDone
  (process-event [{:keys [related-feature-type features area]} app]
    (openlayers/disable-draw!)
    (t/fx (update-in app [:map :search-area] dissoc :drawing?)
          {:tuck.effect/type :rpc
           :rpc "geojson_features_within_area"
           :endpoint (get-in app [:config :api-url])
           :args {"geometry_wkt" area
                  "datasource_ids" (datasource-ids-by-type app related-feature-type)
                  "distance" 0}
           :result-event #(->DrawSelectionResults % related-feature-type features)}))

  DrawSelectionResults
  (process-event [{:keys [related-feature-type features geojson]} app]
    (let [features-by-teet-id (into {} (map (juxt :teet-id identity)) features)
          selected (-> geojson
                       js/JSON.parse
                       (js->clj :keywordize-keys true)
                       :features
                       (as-> fs
                           (into #{}
                                 (keep (comp features-by-teet-id :teet-id :properties))
                                 fs)))]
      (assoc-in app [:route :project :draw-selection-features] selected)))

  DrawSelectionConfirm
  (process-event [{:keys [related-feature-type select?]} app]
    (let [selected (get-in app [:route :project :draw-selection-features])
          checked-feature-path (get-in candidate-paths [related-feature-type :checked-feature-path])
          update-checked (case related-feature-type
                           :restrictions update-checked-restrictions
                           :cadastral-units update-checked-cadastral-units)]
      (openlayers/remove-drawing-layer!)
      (-> app
          (update-in [:route :project] dissoc :draw-selection-features)
          (update-checked
           ((if select? set/union set/difference) ; select or remove selection
            (get-in app checked-feature-path)
            selected)))))

  DrawSelectionCancel
  (process-event [{:keys [related-feature-type]} app]
    (openlayers/remove-drawing-layer!)
    (update-in app [:route :project] dissoc :draw-selection-features)))

(extend-protocol t/Event
  FetchFeatureCandidatesResponse
  (process-event [{:keys [candidate-type response]} app]
    (let [{:keys [result-path geojson-path]} (candidate-paths candidate-type)
          geojson (js/JSON.parse response)
          features (-> geojson
                       (js->clj :keywordize-keys true)
                       :features
                       (as-> fs
                             (map :properties fs)))]
      (-> app
          (update-in [:route :project :feature-candidates] dissoc :loading?)
          (assoc-in result-path features)
          (assoc-in geojson-path geojson))))

  SelectCadastralUnit
  (process-event [{p :p} app]
    (let [cadastral-candidates (get-in app [:route :project :feature-candidates :cadastral-candidates])
          cadastral-selections (get-in app [:route :project :checked-cadastral-units])
          cadastral-unit (or
                           (first (filter #(= (:teet-id %) (:map/teet-id p)) cadastral-selections))
                           (first (filter #(= (:teet-id %) (:map/teet-id p)) cadastral-candidates)))]
      (toggle-cadastral-unit app cadastral-unit)))

  SelectRestriction
  (process-event [{p :p} app]
    (let [restriction-candidates (get-in app [:route :project :feature-candidates :restriction-candidates])
          restriction-selections (get-in app [:route :project :checked-restrictions])
          restriction (or
                        (first (filter #(= (:teet-id %) (:map/teet-id p)) restriction-selections))
                        (first (filter #(= (:teet-id %) (:map/teet-id p)) restriction-candidates)))]

      (if restriction
        (toggle-restriction app restriction)
        app)))

  DeleteActivity
  (process-event [{activity-id :activity-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :activity/delete
           :success-message  (tr [:notifications :activity-deleted])
           :payload          {:db/id (common-controller/->long activity-id)}
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

  SaveProjectOwner
  (process-event [{:keys [owner on-result-callback]} app]
    (let [project-id (get-in app [:params :project])]
      (t/fx app {:tuck.effect/type :command!
                 :command :thk.project/update
                 :payload {:thk.project/id project-id
                           :thk.project/owner owner}
                 :result-event (partial ->SaveProjectOwnerResult on-result-callback)})))

  SaveProjectOwnerResult
  (process-event [{:keys [on-result-callback]} app]
    (t/fx app
          (fn [_e!]
            (on-result-callback))
          common-controller/refresh-fx))

  PostProjectEdit
  (process-event [{:keys [on-result-callback]} app]
    (let [{:thk.project/keys [id]} (get-in app [:route :project])
          {:thk.project/keys [project-name owner km-range m-range-change-reason]}
          (get-in app [:route :project :basic-information-form])
          [start-km end-km] (mapv road-model/parse-km km-range)]
      (t/fx app {:tuck.effect/type :command!
                 :command :thk.project/update
                 :payload (cu/without-nils (merge {:thk.project/id id
                                                   :thk.project/owner owner
                                                   :thk.project/project-name project-name}
                                                  (when m-range-change-reason
                                                    {:thk.project/m-range-change-reason m-range-change-reason})
                                                  (when start-km
                                                    {:thk.project/custom-start-m (road-model/km->m start-km)})
                                                  (when end-km
                                                    {:thk.project/custom-end-m (road-model/km->m end-km)})))
                 :result-event (partial ->PostProjectEditResult on-result-callback)})))

  PostProjectEditResult
  (process-event [{:keys [on-result-callback]} {:keys [params query page] :as app}]
    (t/fx (snackbar-controller/open-snack-bar app (tr [:project :project-updated]))
          (fn [_e!]
            (on-result-callback))
          common-controller/refresh-fx))

  UpdateProjectRestrictions
  (process-event [{restrictions :restrictions
                   project-id :project-id} app]
    (let [restriction-ids (set (map :teet-id restrictions))]
      (t/fx app {:tuck.effect/type :command!
                 :command :thk.project/update-restrictions
                 :payload {:restrictions restriction-ids
                           :project-id project-id}
                 :success-message (tr [:notifications :restrictions-updated])
                 :result-event ->FeaturesUpdatedSuccessfully})))

  FeaturesUpdatedSuccessfully
  (process-event [{result :result} {:keys [page params query] :as app}]
    (t/fx app {:tuck.effect/type :navigate
               :page             page
               :params           params
               :query            (dissoc query :configure)}
          common-controller/refresh-fx))

  UpdateProjectCadastralUnits
  (process-event [{cadastral-units :cadastral-units
                   project-id :project-id} app]
    (let [cadastral-ids (set (map :teet-id cadastral-units))]
      (t/fx app {:tuck.effect/type :command!
                 :command :thk.project/update-cadastral-units
                 :payload {:cadastral-units cadastral-ids
                           :project-id project-id}
                 :success-message (tr [:notifications :cadastral-units-updated])
                 :result-event ->FeaturesUpdatedSuccessfully})))

  InitializeBasicInformationForm
  (process-event [{:keys [form-data]} app]
    (let [app (-> app
                  (update-in [:route :project :basic-information-form]
                             merge form-data)
                  (update-in [:route :project] dissoc :geometry :road-info))
          {:thk.project/keys [road-nr carriageway start-m end-m custom-end-m custom-start-m]} (get-in app [:route :project])]
      (t/fx
       app
       {:tuck.effect/type :query
        :query :road/geometry-with-road-info
        :args {:road road-nr
               :carriageway carriageway
               :start-m (or custom-start-m start-m)
               :end-m (or custom-end-m end-m)}
        :result-event ->RoadGeometryAndInfoResponse})))

  CancelUpdateProjectInformation
  (process-event [_ {:keys [query params page] :as  app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (dissoc query :edit)}
          common-controller/refresh-fx))

  ProjectGeometryFetchSuccess
  (process-event [{result :result} app]
    (js/setTimeout
      #(map-controller/zoom-on-layer "geojson_road_geometry")
      250)
    (assoc-in app [:route :project :geometry] result))

  UpdateBasicInformationForm
  (process-event [{:keys [form-data]} app]
    (let [project-meter-range (fn [app]
                                (let [{[start-km-string end-km-string] :thk.project/km-range}
                                      (get-in app [:route :project :basic-information-form])]
                                  [(or (some-> start-km-string road-model/parse-km road-model/km->m)
                                       (get-in app [:route :project :thk.project/start-m]))
                                   (or (some-> end-km-string road-model/parse-km road-model/km->m)
                                       (get-in app [:route :project :thk.project/end-m]))]))
          old-meter-range (project-meter-range app)
          app (update-in app [:route :project :basic-information-form]
                         merge form-data)
          [new-start-m new-end-m :as new-meter-range] (project-meter-range app)

          ;; Fetch new road geometry if it hasn't been fetched yet or has changed
          fetch-geometry? (or (not (contains? (get-in app [:route :project]) :geometry))
                              (not= old-meter-range new-meter-range))]
      (t/fx
       (if fetch-geometry?
         ;; Remove previously fetched geometry
         (update-in app [:route :project] dissoc :geometry)
         app)

       (when fetch-geometry?
         {:tuck.effect/type :query
          :query :road/geometry
          :args {:road (get-in app [:route :project :thk.project/road-nr])
                 :carriageway (get-in app [:route :project :thk.project/carriageway])
                 :start-m new-start-m
                 :end-m new-end-m}
          :result-event ->ProjectGeometryFetchSuccess}))))

  RoadGeometryAndInfoResponse
  (process-event [{result :result} app]
    (update-in app [:route :project] merge result))

  FetchRelatedCandidates
  (process-event [{road-buffer-meters :road-buffer-meters
                   entity-type :entity-type}
                  app]
    (t/fx app
          (fetch-related-candidates app road-buffer-meters (or (project-setup-step app)
                                                               entity-type))))

  FetchRelatedFeatures
  (process-event [{feature-ids :feature-ids
                   feature-type :feature-type} app]
    (t/fx app
          {:tuck.effect/type :rpc
           :endpoint (get-in app [:config :api-url])
           :rpc "geojson_features_by_id"
           :args {"ids" feature-ids}
           :result-event (partial ->RelatedFeaturesSuccess feature-type)}))

  RelatedFeaturesSuccess
  (process-event [{type :type
                   result :result} app]
    (let [{:keys [checked-feature-path checked-feature-geojson-path]} (candidate-paths type)
          geojson (js/JSON.parse result)
          features (-> geojson
                       (js->clj :keywordize-keys true)
                       :features
                       (as-> fs
                             (map :properties fs))
                       set)
          feature-geojsons (->> geojson
                                ->clj
                                :features
                                set)]
      (-> app
          (assoc-in checked-feature-path features)
          (assoc-in checked-feature-geojson-path feature-geojsons))))

  ToggleRestriction
  (process-event [{restriction :restriction} app]
    (toggle-restriction app restriction))

  SelectRestrictions
  (process-event [{restrictions :restrictions} app]
    (update-checked-restrictions
     app
     (set/union (get-in app [:route :project :checked-restrictions] #{})
                restrictions)))

  DeselectRestrictions
  (process-event [{restrictions :restrictions} app]
    (update-checked-restrictions
     app
     (set/difference (get-in app [:route :project :checked-restrictions] #{})
                     restrictions)))

  ToggleCadastralUnit
  (process-event [{cadastral-unit :cadastral-unit} app]
    (toggle-cadastral-unit app cadastral-unit))

  SelectCadastralUnits
  (process-event [{cadastral-units :cadastral-units} app]
    (update-checked-cadastral-units
     app
     (set/union (get-in app [:route :project :checked-cadastral-units] #{})
                cadastral-units)))

  DeselectCadastralUnits
  (process-event [{cadastral-units :cadastral-units} app]
    (update-checked-cadastral-units
     app
     (set/difference (get-in app [:route :project :checked-cadastral-units] #{})
                     cadastral-units)))

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
          restriction-geojsons (get-in app [:route :project :feature-candidates :restriction-candidates-geojson])
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
           :query            :thk.project/integration-id->thk-id
           :args             {:integration/id (common-controller/->long id)}
           :result-event     ->NavigateToProject}))

  NavigateToProject
  (process-event [{id :thk-project-id} app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             :project
           :params           {:project id}}))

  OpenActivityDialog
  (process-event [{lifecycle :lifecycle} {:keys [page params query] :as app}]
    (-> app
        (assoc-in [:stepper :dialog]
                  {:type :new-activity
                   :lifecycle-type (-> app
                                       common-controller/page-state
                                       (project-model/lifecycle-by-id lifecycle)
                                       :thk.lifecycle/type :db/ident)})
        (assoc :edit-activity-data {})))

  OpenPeopleModal
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :modal "people")}))

  SaveProjectPermissionResult
  (process-event [{reset-form-value-event :reset-form-value-event} app]
    (t/fx app
          (fn [e!]
            (e! (reset-form-value-event)))
          common-controller/refresh-fx))

  SaveProjectPermission
  (process-event [{project-id :project-id form-data :form-data
                   reset-form-value-event :reset-form-value-event} app]
    (let [participant (:project/participant form-data)
          role (:permission/role form-data)]
      (t/fx app
            {:tuck.effect/type :command!
             :command :thk.project/add-permission
             :payload {:project-id project-id
                       :user (if (= participant :new)
                               {:user/person-id (:user/person-id form-data)}
                               participant)
                       :role role}
             :success-message (tr [:notifications :permission-added-successfully])
             :result-event (partial ->SaveProjectPermissionResult reset-form-value-event)})))


  RevokeProjectPermission
  (process-event [{permission-id :permission-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :thk.project/revoke-permission
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
    (update-in app [:route :project :add-participant]
               (fn [form]
                 (let [new-form (merge form form-data)]
                   (if (contains? form-data :project/participant)
                     (dissoc new-form :user/person-id)
                     new-form)))))


  CloseDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx (update app :stepper dissoc :dialog)
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (dissoc query :modal :add :edit :activity :lifecycle :modal-target :modal-page :modal-new-opinion)}))

  OpenEditDetailsDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (assoc query :edit "details")}))

  OpenEditProjectDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :edit "project")}))

  OpenEditActivityDialog
  (process-event [{activity-id :activity-id} app]
    (-> app
        (assoc-in [:stepper :dialog] {:type :edit-activity})
        (assoc :edit-activity-data
               (-> app common-controller/page-state
                   (project-model/activity-by-id activity-id)
                   (update :activity/name :db/ident)))))

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
      (update-in app [:stepper] dissoc :lifecycle)
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
