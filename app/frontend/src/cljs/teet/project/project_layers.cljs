(ns teet.project.project-layers
  "Map layers for project view"
  (:require [teet.map.map-layers :as map-layers]
            [teet.map.map-controller :as map-controller]
            [teet.map.map-features :as map-features]
            [teet.road.road-model :as road-model :refer [km->m]]
            [teet.map.map-view :as map-view]
            [teet.project.project-model :as project-model]
            [teet.project.project-style :as project-style]
            [clojure.string :as str]
            [teet.log :as log]))

(defn- endpoint [app]
  (get-in app [:config :api-url]))

(defn surveys-layer [app _project _overlays]
  {:surveys (map-layers/mvt-layer (endpoint app)
                                  "mvt_features"
                                  {"datasource" (map-controller/datasource-id-by-name app "survey")
                                   "types"      "{}"}
                                  map-features/survey-style
                                  {:opacity 0.5})})

(defn- km-range-label-overlays [start-label end-label callback {source :source}]
  (when-let [geom (some-> ^ol.source.Vector source
                          .getFeatures
                          (aget 0)
                          .getGeometry)]
    (let [start (.getFirstCoordinate geom)
          end (.getLastCoordinate geom)]
      (callback
        [{:coordinate (js->clj start)
          :content    [map-view/overlay {:arrow-direction :right :height 30}
                       start-label]}
         {:coordinate (js->clj end)
          :content    [map-view/overlay {:arrow-direction :left :height 30}
                       end-label]}]))))

(defn given-range-in-actual-road?
  "Check to see if the forms given road range is in the actual road"
  [{:keys [end_m start_m]} [form-start-m form-end-m]]
  (and (> form-end-m form-start-m)
       (>= form-start-m start_m)
       (>= end_m form-end-m)))

(defn project-road-geometry-layer
  "Show project geometry or custom road part in case the start and end
  km are being edited during initialization"
  [app
   {:thk.project/keys [start-m end-m road-nr carriageway]
    :keys             [basic-information-form] :as project}
   overlays]
  (let [endpoint (endpoint app)
        [start-label end-label]
        (if basic-information-form
          (mapv (comp road-model/format-distance
                      km->m
                      road-model/parse-km)
                (:thk.project/km-range basic-information-form))
          (mapv (comp road-model/format-distance
                      km->m)
                (project-model/get-column project
                                          :thk.project/effective-km-range)))
        road-information (:road-info basic-information-form)
        options {:fit-on-load? true
                 ;; Use left side padding so that road is not shown under the project panel
                 :fit-padding  [0 0 0 (* 1.05 (project-style/project-panel-width))]
                 :on-load      (partial km-range-label-overlays
                                        start-label end-label
                                        #(reset! overlays %))}]

    {:thk-project
     (if basic-information-form
       (let [{[start-km-string end-km-string] :thk.project/km-range} basic-information-form
             form-start-m (some-> start-km-string road-model/parse-km km->m)
             form-end-m (some-> end-km-string road-model/parse-km km->m)]
         (if (given-range-in-actual-road? road-information [form-start-m form-end-m])
           (map-layers/geojson-layer
            endpoint
            "geojson_road_geometry"
            {"road"        road-nr
             "carriageway" carriageway
             "start_m"     form-start-m
             "end_m"       form-end-m}
            map-features/project-line-style
            (merge options
                   {:content-type "application/json"}))
           ;; Needed to remove road ending markers
           (do (reset! overlays nil)
               nil)))

       (map-layers/geojson-layer endpoint
                                 "geojson_entities"
                                 {"ids" (str "{" (:db/id project) "}")}
                                 map-features/project-line-style
                                 options))}))

(defn setup-restriction-candidates [_app {:keys [setup-step
                                                 restriction-candidates-geojson]} _overlays]
  (when-let [candidates (and (= setup-step "restrictions")
                             restriction-candidates-geojson)]
    {:related-restriction-candidates
     (map-layers/geojson-data-layer "related-restriction-candidates"
                                    candidates
                                    map-features/project-related-restriction-style
                                    {:opacity 0.5})}))

(defn setup-cadastral-unit-candidates [_app {:keys [setup-step
                                                    cadastral-candidates-geojson]} _overlays]
  (when-let [candidates (and (= setup-step "cadastral-units")
                             cadastral-candidates-geojson)]
    {:related-cadastral-unit-candidates
     (map-layers/geojson-data-layer "related-cadastral-unit-candidates"
                                    candidates
                                    map-features/cadastral-unit-style
                                    {:opacity 0.5})}))


(defn project-road-buffer-layer
  "Show buffer area for project-geometry"
  [{:thk.project/keys [road-nr carriageway]
    :keys             [basic-information-form] :as _project}
   endpoint
   road-buffer-meters]
  (let [road-information (:road-info basic-information-form)]
    (when (and basic-information-form)
      (let [{[start-km-string end-km-string] :thk.project/km-range} basic-information-form
            form-start-m (some-> start-km-string road-model/parse-km km->m)
            form-end-m (some-> end-km-string road-model/parse-km km->m)]
        (when (given-range-in-actual-road? road-information [form-start-m form-end-m])
          (map-layers/geojson-layer
           endpoint
            "geojson_road_buffer_geometry"
            {"road"        road-nr
             "carriageway" carriageway
             "start_m"     form-start-m
             "end_m"       form-end-m
             "buffer"      road-buffer-meters}
            map-features/road-buffer-fill-style
            {:content-type "application/json"}))))))

(defn road-buffer [app project _overlays]
  (let [road-buffer-meters (get-in app [:map :road-buffer-meters])]
    (when (and (not-empty road-buffer-meters)
               (>= road-buffer-meters 0))
      {:thk-project-buffer
       (project-road-buffer-layer project (endpoint app) road-buffer-meters)})))


(defn related-restrictions [app {restrictions :thk.project/related-restrictions} _overlays]
  (when restrictions
    {:related-restrictions
     (map-layers/geojson-layer (endpoint app)
                               "geojson_features_by_id"
                               {"ids" (str "{" (str/join "," restrictions) "}")}
                               map-features/project-restriction-style
                               {})}))

(defn related-cadastral-units [app {cadastral-units :thk.project/related-cadastral-units} _overlays]
  (when cadastral-units
    {:related-cadastral-units
     (map-layers/geojson-layer (endpoint app)
                               "geojson_features_by_id"
                               {"ids" (str "{" (str/join "," cadastral-units) "}")}
                               map-features/cadastral-unit-style
                               {})}))

(defn ags-surveys [app project _overlays]
  (reduce
   (fn [layers file]
     (if (str/ends-with? (:file/name file) ".ags")
       (assoc layers
              (str "ags-survey-" (:db/id file))
              (map-layers/mvt-layer (endpoint app)
                                    "mvt_entity_features"
                                    {"entity" (str (:db/id file))
                                     "types" "{}"}
                                    map-features/ags-survey-style
                                    {}))
       layers))
   {}
   (project-model/project-files project)))
