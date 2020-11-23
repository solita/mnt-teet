(ns teet.project.project-layers
  "Map layers for project view"
  (:require [teet.map.map-layers :as map-layers]
            [teet.map.map-controller :as map-controller]
            [teet.map.map-features :as map-features]
            [teet.road.road-model :as road-model :refer [km->m]]
            [cljs-bean.core :refer [->js]]
            [teet.map.map-view :as map-view]
            [teet.project.project-model :as project-model]
            [clojure.string :as str]
            [reagent.core :as r]
            [teet.map.map-overlay :as map-overlay]))

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
    :content [map-overlay/overlay {:arrow-direction :right :height 30}
              start-label]}
   {:coordinate end-coordinate
    :content [map-overlay/overlay {:arrow-direction :left :height 30}
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

(defn project-drawn-area-layer
  [{app :app}]
  (let [drawn-geojsons (get-in app [:map :search-area :drawn-areas-geojson])
        tab-drawn? (boolean (= (get-in app [:map :search-area :tab]) :drawn-area))]
    (when (and tab-drawn? drawn-geojsons)
      {:drawn-area-style
       (map-layers/geojson-data-layer "project-drawn-areas"
                                      drawn-geojsons
                                      map-features/drawn-area-style
                                      {})})))

(defn geometry-fit-on-first-load
  "Get atom as parameter that has info if the geometry fit has happened
   And switch the atom state if it's the first time fit is happening"
  [fitted-atom]
  (if @fitted-atom
    false
    (do
      (reset! fitted-atom true)
      true)))

(defn project-road-geometry-layer
  "Show project geometry or custom road part in case the start and end
  km are being edited during initialization"
  [map-obj-padding
   fitted-atom
   {app :app
    {:keys [basic-information-form geometry] :as project} :project
    set-overlays! :set-overlays!}]
  (let [tab-drawn? (boolean (= (get-in app [:map :search-area :tab]) :drawn-area))
        endpoint (endpoint app)
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
        options {:fit-on-load? (geometry-fit-on-first-load fitted-atom)
                 ;; Use left side padding so that road is not shown under the project panel
                 :fit-padding map-obj-padding}

        ;; If we have geometry (in setup wizard) or are editing related features
        ;; show the buffer as well, otherwise just the road line
        style (if (and
                    (not tab-drawn?)
                    (#{"restrictions" "cadastral-units"} (get-in app [:query :configure])))
                (map-features/project-line-style-with-buffer (get-in app [:map :road-buffer-meters]))
                map-features/project-line-style)]

    {:thk-project
     (if geometry
       (do
         ;; Set start/end labels directly from geometry
         (set-overlays! (km-range-label-overlays start-label (first geometry)
                                                 end-label (last geometry)))
         (map-layers/geojson-data-layer
          "geojson_road_geometry"
          (line-string-to-geojson geometry)
          style
          options))

       (map-layers/geojson-layer endpoint
                                 "geojson_entities"
                                 {"ids" (str "{" (:integration/id project) "}")}
                                 style
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
                                                feature-candidates]} :project}]
  (let [{:keys [cadastral-candidates-geojson]} feature-candidates]
    (when (and (or
                 (= setup-step "cadastral-units")
                 (= (:configure query) "cadastral-units"))
               (get (js->clj cadastral-candidates-geojson) "features"))
      {:related-cadastral-unit-candidates
       (map-layers/geojson-data-layer "related-cadastral-unit-candidates"
                                      cadastral-candidates-geojson
                                      map-features/cadastral-unit-style
                                      {:opacity 1})})))

(defn related-restrictions [{{query :query :as app} :app
                             {restrictions :thk.project/related-restrictions} :project}]
  (when (and restrictions (not (or (:configure query) (= (:tab query) "land"))))
    {:related-restrictions
     (map-layers/geojson-layer (endpoint app)
                               "geojson_features_by_id"
                               {"ids" restrictions}
                               map-features/project-related-restriction-style
                               {:post? true})}))

(defn related-cadastral-units [{{query :query :as app} :app
                                {cadastral-units :thk.project/related-cadastral-units
                                 filtered-units :thk.project/filtered-cadastral-units} :project}]
  (when (and cadastral-units (not (:configure query)))
    (let [units (or filtered-units cadastral-units)]
      {:related-cadastral-units
       (map-layers/geojson-layer (endpoint app)
                                 "geojson_features_by_id"
                                 {"ids" units}
                                 map-features/cadastral-unit-style
                                 {:post? true})})))

(defn- ags-on-select [e! {:map/keys [teet-id]}]
  (e! (map-controller/->FetchOverlayForEntityFeature [:route :project :overlays] teet-id)))

(defn selected-cadastral-units [{{:keys [query]} :app
                                 {checked-cadastral-geojson :checked-cadastral-geojson
                                  setup-step :setup-step} :project}]
  (when (and (not-empty (js->clj checked-cadastral-geojson))
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

  (when (and (not-empty (js->clj checked-restrictions-geojson))
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

(defn highlighted-road-objects [{{geometries :road/highlight-geometries} :project
                                 {query :query} :app}]
  (when (and (= "road" (:tab query))
             (seq geometries))
    {:higlighted-road-objects
     (map-layers/geojson-data-layer
      "highlighted-road-objects"
      #js {:type "FeatureCollection"
           :features (into-array
                      (for [geom geometries]
                        #js {:type "Feature"
                             :properties #js {}
                             :geometry (clj->js geom)}))}
      map-features/highlighted-road-object-style
      {:z-index 999
       :opacity 0.7})}))
