(ns teet.map.map-controller
  "Controller for map components"
  (:require [tuck.core :as t]
            [teet.map.openlayers :as openlayers]
            [teet.log :as log]
            [teet.common.common-controller :as common-controller]
            [clojure.string :as str]
            [cljs-bean.core :refer [->clj]]))

(defn atleast-one-open?
  [layers]
  (some identity (vals layers)))

(defrecord FetchMapLayers [])
(defrecord MapLayersResult [result])
(defrecord LayerToggle [category layer])
(defrecord ToggleMapControls [])
(defrecord ToggleCategoryCollapse [category])
(defrecord ToggleCategorySelect [category closing?])
(defrecord CloseMapControls [])
(defrecord SetBackgroundLayer [layer])
(defrecord FetchDatasources [])

(defrecord FetchOverlayForEntityFeature [result-path teet-id])
(defrecord FetchOverlayForEntityFeatureResponse [result-path teet-id response])

;; Events for adding/removing/editing visible layers
(defrecord AddLayer []) ; open new layer dialog
(defrecord EditLayer [layer]) ; open layer edit dialog
(defrecord SaveLayer []) ; save new/edited layer
(defrecord CancelLayer []) ; cancel add/edit layer
(defrecord UpdateLayer [layer]) ; update layer with new data
(defrecord RemoveLayer [layer]) ; remove layer
(defrecord LayerInfoResponse [layer-id key layer-info]) ; merge fetched extra layer info
(declare maybe-fetch-layer-info)

(let [type-and-id #(select-keys % [:type :id])]
  (defn- layer= [l1 l2]
    (= (type-and-id l1)
       (type-and-id l2))))

(extend-protocol t/Event
  ToggleCategorySelect
  (process-event [{:keys [category closing?]} app]
    (update-in app
               [:map :map-restrictions category]
               (fn [layers]
                 (let [closing? (atleast-one-open? layers)]
                   (into {}
                         (map
                           (fn [[layer-name _]]
                             {layer-name (not closing?)}))
                         layers)))))

  ToggleMapControls
  (process-event [_ app]
    (update-in app [:map :map-controls :open?] not))

  CloseMapControls
  (process-event [_ app]
    (update-in app [:map :map-controls :open?] false))

  CloseMapControls
  (process-event [_ app]
    (assoc-in app [:map :map-controls :open?] false))

  ToggleCategoryCollapse
  (process-event [{:keys [category]} app]
    (update-in app [:map :map-controls category :collapsed?] not))

  LayerToggle
  (process-event [{:keys [category layer]} app]
    (update-in app [:map :map-restrictions category layer] not))

  MapLayersResult
  (process-event [result app]
    (let [layers (:result result)
          formatted (into {}
                          (map
                           (fn [{:keys [type layers]}]
                             (let [layers (into {}
                                                (map
                                                 (fn [layer]
                                                   [layer false]))
                                                (filter some? layers))]
                               [type layers])))
                          layers)]
      (assoc-in app [:map :map-restrictions] (assoc formatted "Katastri" {"katastriyksus" false}) ;;Added because cadastral units are not in fetched data
                )))

  FetchMapLayers
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :rpc
           :endpoint         (get-in app [:config :api-url])
           :rpc              "restriction_map_selections"
           :args             {}
           :result-event     ->MapLayersResult}))

  SetBackgroundLayer
  (process-event [{layer :layer} app]
    (log/info "ASETA" layer)
    (assoc-in app [:map :background-layer] layer))

  FetchDatasources
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :rpc
           :endpoint (get-in app [:config :api-url])
           :rpc "datasources"
           :args {}
           :result-path [:map :datasources]}))

  FetchOverlayForEntityFeature
  (process-event [{:keys [result-path teet-id]} app]
    (t/fx app
          {:tuck.effect/type :rpc
           :endpoint (get-in app [:config :api-url])
           :rpc "geojson_entity_features_by_id"
           :json? true
           :args {:ids [teet-id]}
           :result-event (partial ->FetchOverlayForEntityFeatureResponse result-path teet-id)}))

  FetchOverlayForEntityFeatureResponse
  (process-event [{:keys [result-path teet-id response]} app]
    (let [geojson (-> response js/JSON.parse ->clj)
          feature (-> geojson :features first)]
      (assoc-in app
                result-path
                {teet-id
                 {:coordinate (-> feature :geometry :coordinates)
                  :content-data (-> feature :properties)}})))

  AddLayer
  (process-event [_ app]
    (assoc-in app [:map :edit-layer]
              {:new? true}))

  EditLayer
  (process-event [{layer :layer} app]
    (assoc-in app [:map :edit-layer] layer))

  UpdateLayer
  (process-event [{:keys [layer]} app]
    (update-in app [:map :edit-layer] merge layer))

  SaveLayer
  (process-event [_ app]
    (let [{:keys [new?] :as layer} (get-in app [:map :edit-layer])
          layer (-> layer
                    (dissoc :new?)
                    (update :id #(or % (random-uuid))))]
      (maybe-fetch-layer-info
       (-> app
           (update-in [:map :layers]
                      (fn [layers]
                        (if new?
                          (conj (or layers []) layer)
                          (mapv #(if (layer= layer %)
                                   layer
                                   %)
                                layers))))
           (update :map dissoc :edit-layer))
       layer)))

  LayerInfoResponse
  (process-event [{:keys [layer-id key layer-info]} app]
    (update-in app [:map :layers]
               #(mapv (fn [{id :id :as layer}]
                        (if (= id layer-id)
                          (assoc layer key layer-info)
                          layer))
                      %)))

  CancelLayer
  (process-event [_ app]
    (update app :map dissoc :edit-layer))

  RemoveLayer
  (process-event [{layer :layer} app]
    (-> app
        (update-in [:map :layers]
                   (fn [layers]
                     (into []
                           (keep #(when (not (layer= layer %))
                                    %))
                           layers)))
        (update :map dissoc :edit-layer))))

(defn update-features! [layer-name update-fn & args]
  (let [^ol.Map m (openlayers/get-the-map)]
    (-> m
        .getLayers
        (.forEach (fn [layer]
                    (when (= layer-name (.get layer "teet-source"))
                      (-> layer
                          .getSource
                          .getFeatures
                          (.forEach
                           (fn [item]
                             (apply update-fn item args))))))))))

(defn zoom-on-layer [layer-name]
  (let [^ol.Map m (openlayers/get-the-map)]
    (-> m
        .getLayers
        (.forEach (fn [layer]
                    (when (= layer-name (.get layer "teet-source"))
                      (-> layer
                          .getSource
                          .getExtent
                          (openlayers/fit! {:padding [0 0 0 300]}))))))))

(defn zoom-on-feature [layer-name unit]
  (let [^ol.Map m (openlayers/get-the-map)]
    (-> m
        .getLayers
        (.forEach (fn [layer]
                    (when (= layer-name (.get layer "teet-source"))
                      (-> layer
                          .getSource
                          .getFeatures
                          (.forEach
                            (fn [item]
                              (let [id (.get item "teet-id")]
                                (when (= id (:teet-id unit))
                                  (let [extent (-> item
                                                   .getGeometry
                                                   .getExtent
                                                   vec)]
                                    (openlayers/fit! extent {:padding [0 0 0 300]})))))))))))))

(common-controller/register-init-event! :fetch-datasources ->FetchDatasources)

(defn datasources
  "Return datasource definitions from the given app state."
  [app]
  (get-in app [:map :datasources]))

(defn select-rpc-datasources
  "Returns selected datasources as a parameter for RPC calls.
  Returns the id of all datasources that match given predicate."
  [app pred]
  (let [ds (->> app datasources
                (filter pred)
                (map :id))]
    (str "{" (str/join "," ds) "}")))

(defn cadastral-unit-datasource? [{name :name}]
  (= name "cadastral-units"))

(defn restriction-datasource? [{name :name}]
  (str/starts-with? name "restrictions:"))

(defn datasource-id-by-name [app name]
  (->> app datasources
       (filter #(= (:name %) name))
       first
       :id))

(defn maybe-fetch-layer-info
  "Return effects to fetch extra information
  needed by created/edited layer."
  [app {:keys [id type] :as layer}]
  (cond
    (= type :projects)
    (let [filters (into {}
                        (keep (fn [[key val]]
                                (when-not (or (nil? val)
                                              (and (string? val)
                                                   (str/blank? val)))
                                  [key val])))
                        (select-keys layer [:text :road :km :region :date]))]

      (t/fx app
            (fn [e!]
              (e! (->LayerInfoResponse id :projects
                                       (when (seq filters)
                                         []
                                         nil))))

            (when (seq filters)
              {:tuck.effect/type :query
               :query :thk.project/search
               :args filters
               :result-event (partial ->LayerInfoResponse id :projects)})))

    :else
    app))
