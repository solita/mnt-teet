(ns teet.project.project-layers
  "Map layers for project view"
  (:require [teet.map.map-layers :as map-layers]
            [teet.map.map-controller :as map-controller]
            [teet.map.map-features :as map-features]
            [teet.road.road-model :as road-model :refer [km->m]]
            [cljs-bean.core :refer [->clj ->js]]
            [teet.map.map-view :as map-view]
            [teet.project.project-model :as project-model]
            [teet.project.project-style :as project-style]
            [clojure.string :as str]
            [teet.log :as log]
            [reagent.core :as r]))

(defn- endpoint [app]
  (get-in app [:config :api-url]))

(defn surveys-layer [{app :app}]
  {:surveys (map-layers/mvt-layer (endpoint app)
                                  "mvt_features"
                                  {"datasource" (map-controller/datasource-id-by-name app "survey")
                                   "types" "{}"}
                                  map-features/survey-style
                                  {:opacity 0.5})})

(defn- km-range-label-overlays [start-label start-coordinate
                                end-label end-coordinate]
  [{:coordinate start-coordinate
    :content [map-view/overlay {:arrow-direction :right :height 30}
              start-label]}
   {:coordinate end-coordinate
    :content [map-view/overlay {:arrow-direction :left :height 30}
              end-label]}])

(defn- update-km-range-label-overlays! [start-label end-label callback {source :source}]
  (when-let [geom (some-> ^ol.source.Vector source
                          .getFeatures
                          (aget 0)
                          .getGeometry)]
    (let [start (.getFirstCoordinate geom)
          end (.getLastCoordinate geom)]
      (callback
       (km-range-label-overlays
        start-label (js->clj start)
        end-label (js->clj end))))))

(defn given-range-in-actual-road?
  "Check to see if the forms given road range is in the actual road"
  [{:keys [end_m start_m]} [form-start-m form-end-m]]
  (and (> form-end-m form-start-m)
       (>= form-start-m start_m)
       (>= end_m form-end-m)))

(defn- line-string-to-geojson [ls]
  #js {:type "FeatureCollection"
       :features
       #js [#js {:type "Feature"
                 :properties #js {}
                 :geometry #js {:type "LineString"
                                :coordinates (into-array (map into-array ls))}}]})

(defn project-road-geometry-layer
  "Show project geometry or custom road part in case the start and end
  km are being edited during initialization"
  [map-obj-padding
   {app :app
    {:thk.project/keys [road-nr carriageway]
     :keys [basic-information-form geometry] :as project} :project
    set-overlays! :set-overlays!}]

  (let [endpoint (endpoint app)
        [start-label end-label]
        (if-let [km-range (:thk.project/km-range basic-information-form)]
          (mapv (comp road-model/format-distance
                      km->m
                      road-model/parse-km)
                km-range)
          (mapv (comp road-model/format-distance
                      km->m)
                (project-model/get-column project
                                          :thk.project/effective-km-range)))
        options {:fit-on-load? true
                 ;; Use left side padding so that road is not shown under the project panel
                 :fit-padding map-obj-padding}]

    {:thk-project
     (if geometry
       (do
         ;; Set start/end labels directly from geometry
         (set-overlays! (km-range-label-overlays start-label (first geometry)
                                                 end-label (last geometry)))
         (map-layers/geojson-data-layer
          "geojson_road_geometry"
          (line-string-to-geojson geometry)
          (map-features/project-line-style-with-buffer (get-in app [:map :road-buffer-meters]))
          options))

       (map-layers/geojson-layer endpoint
                                 "geojson_entities"
                                 {"ids" (str "{" (:db/id project) "}")}
                                 map-features/project-line-style
                                 (assoc options
                                        ;; Update start/end labels from source geometry
                                        ;; once it is loaded
                                        :on-load (partial update-km-range-label-overlays!
                                                          start-label end-label
                                                          set-overlays!))))}))

(defn setup-restriction-candidates [{{:keys [query]} :app
                                     {:keys [setup-step
                                             open-restrictions-geojsons]} :project}]
  (when-let [candidates (and (or
                               (= setup-step "restrictions")
                               (= (:configure query) "restrictions"))
                             open-restrictions-geojsons)]
    {:related-restriction-candidates
     (map-layers/geojson-data-layer "related-restriction-candidates"
                                    candidates
                                    map-features/project-related-restriction-style
                                    {:opacity 1})}))

(defn setup-cadastral-unit-candidates [{{:keys [query]} :app
                                        {:keys [setup-step
                                                cadastral-candidates-geojson]} :project}]
  (when-let [candidates (and (or
                               (= setup-step "cadastral-units")
                               (= (:configure query) "cadastral-units"))
                             cadastral-candidates-geojson)]
    {:related-cadastral-unit-candidates
     (map-layers/geojson-data-layer "related-cadastral-unit-candidates"
                                    candidates
                                    map-features/cadastral-unit-style
                                    {:opacity 1})}))


;; PENDING: not needed anymore, we draw the buffer with a different stroke
#_(defn road-buffer [{{:keys [query] :as app} :app {:keys [basic-information-form]
                                                  :thk.project/keys [start-m end-m road-nr carriageway] :as project} :project}]
  (let [road-information (:road-info basic-information-form)
        configure (:configure query)
        {[start-km-string end-km-string] :thk.project/km-range} basic-information-form
        road-start-m (or (some-> start-km-string road-model/parse-km km->m) start-m)
        road-end-m (or (some-> end-km-string road-model/parse-km km->m) end-m)
        road-buffer-meters (get-in app [:map :road-buffer-meters])
        road-info {:start-m road-start-m
                   :end-m road-end-m
                   :road-nr road-nr
                   :carriageway carriageway}]
    (when (and (not-empty road-buffer-meters)
               (>= road-buffer-meters 0)
               (or
                 configure
                 (and
                  basic-information-form
                  #_(given-range-in-actual-road? road-information [road-start-m road-end-m]))))
      {:thk-project-buffer
       (project-road-buffer-layer road-info (endpoint app) road-buffer-meters)})))


(defn related-restrictions [{{query :query :as app} :app
                             {restrictions :thk.project/related-restrictions} :project}]
  (when (and restrictions (not (:configure query)))
    {:related-restrictions
     (map-layers/geojson-layer (endpoint app)
                               "geojson_features_by_id"
                               {"ids" (str "{" (str/join "," restrictions) "}")}
                               map-features/project-restriction-style
                               {})}))

(defn related-cadastral-units [{{query :query :as app} :app
                                {cadastral-units :thk.project/related-cadastral-units} :project}]
  (when (and cadastral-units (not (:configure query)))
    {:related-cadastral-units
     (map-layers/geojson-layer (endpoint app)
                               "geojson_features_by_id"
                               {"ids" (str "{" (str/join "," cadastral-units) "}")}
                               map-features/cadastral-unit-style
                               {})}))

(defn- ags-on-select [e! {:map/keys [teet-id]}]
  (e! (map-controller/->FetchOverlayForEntityFeature [:route :project :overlays] teet-id)))

(defn selected-cadastral-units [{{:keys [query]} :app
                                 {checked-cadastral-geojson :checked-cadastral-geojson
                                  setup-step :setup-step} :project}]
  (when (and checked-cadastral-geojson
             (or
               (= (:configure query) "cadastral-units")
               (= setup-step "cadastral-units")))
    {:checked-cadastral-geojson
     (map-layers/geojson-data-layer "selected-cadastral-units"
                                    (->js (assoc {"type" "FeatureCollection"} "features" (into [] checked-cadastral-geojson)))
                                    map-features/selected-cadastral-unit-style
                                    {:opacity 1})}))

(defn selected-restrictions [{{:keys [query]} :app
                              {checked-restrictions-geojson :checked-restrictions-geojson
                               setup-step :setup-step} :project}]
  (when (and checked-restrictions-geojson
             (or
               (= (:configure query) "restrictions")
               (= setup-step "restrictions")))
    {:checked-restrictions-geojson
     (map-layers/geojson-data-layer "selected-restrictions"
                                    (->js (assoc {"type" "FeatureCollection"} "features" (into [] checked-restrictions-geojson)))
                                    map-features/selected-restrictions-style
                                    {:opacity 1})}))

(defn ags-surveys [{:keys [e! app project]}]
  (reduce
    (fn [layers file]
      (if (str/ends-with? (:file/name file) ".ags")
        (assoc layers
          (str "ags-survey-" (str (:db/id file)))
          (map-layers/mvt-layer (endpoint app)
                                "mvt_entity_features"
                                {"entity" (str (:db/id file))
                                 "types" "{}"}
                                map-features/ags-survey-style
                                {:on-select (r/partial ags-on-select e!)}))
        layers))
    {}
    (project-model/project-files project)))
