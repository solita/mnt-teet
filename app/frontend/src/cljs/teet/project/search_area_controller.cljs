(ns teet.project.search-area-controller
  (:require [tuck.core :as t]
            [teet.project.project-controller :as project-controller]
            [teet.map.openlayers :as openlayers]
            [teet.map.map-controller :as map-controller]))

(defrecord ChangeRoadObjectAoe [val entity-type])
(defrecord InitializeCustomAreaDraw [])
(defrecord StopCustomAreaDraw [])
(defrecord SaveDrawnArea [area])
(defrecord ChangeTab [new-tab])
(defrecord SaveDrawnAreaSuccess [result])
(defrecord DrawnAreaSuccess [result])
(defrecord FetchDrawnAreas [])
(defrecord UnMountSearchComponent [])
(defrecord DeleteDrawnArea [geometry-id entity-id])
(defrecord DeleteSuccess [result])
(defrecord MouseOverDrawnAreas [layer enter? feature])

(defn info-type
  "Check from app state which info should be fetched. Cadastral or restrictions
  If neither, returns nil."
  [app]
  (if (or (= "basic-information" (project-controller/project-setup-step app))
          (nil? (project-controller/project-setup-step app)))
    (get-in app [:query :configure])
    (project-controller/project-setup-step app)))

(defn fetch-drawn-areas
  [app]
  (let [entity-id (str (get-in app [:route :project :db/id]))]
    {:tuck.effect/type :rpc
     :rpc "geojson_entity_features_by_type"
     :endpoint (get-in app [:config :api-url])
     :args {:type "search-area"
            :entity_id entity-id}
     :result-event ->DrawnAreaSuccess}))

(extend-protocol t/Event
  ChangeRoadObjectAoe
  (process-event [{val :val
                   entity-type :entity-type} app]
    (let [app (assoc-in app [:map :road-buffer-meters] val)]
      (if (and entity-type (not-empty val))
        (t/fx app
              {:tuck.effect/type :debounce
               :timeout 600
               :effect (project-controller/fetch-related-info app val entity-type)})
        app)))

  UnMountSearchComponent
  (process-event [_ app]
    (update-in app [:map :search-area] dissoc :tab))

  SaveDrawnArea
  (process-event [{area :area} app]
    (let [project-id (get-in app [:route :project :db/id])]
      (openlayers/disable-draw!)
      (t/fx (update-in app [:map :search-area] dissoc :drawing?)
            {:tuck.effect/type :command!
             :command :thk.project/add-search-geometry
             :payload {:geometry area
                       :geometry-label "Area"
                       :project-db-id project-id}
             :result-event ->SaveDrawnAreaSuccess})))

  FetchDrawnAreas
  (process-event [_ app]
    (t/fx app
          (fetch-drawn-areas app)))

  SaveDrawnAreaSuccess
  (process-event [{_result :result} app]
    (let [info-type (info-type app)]
      (t/fx app
            (fetch-drawn-areas app)
            (when info-type
              (project-controller/fetch-related-info app 0 info-type)))))

  DrawnAreaSuccess
  (process-event [{result :result} app]
    (let [geojson (js/JSON.parse result)
          features (-> geojson
                       (js->clj :keywordize-keys true)
                       :features
                       (as-> fs
                             (map :properties fs)))]
      (if (not-empty features)
        (-> app
            (assoc-in [:map :search-area :drawn-areas-geojson] geojson)
            (assoc-in [:map :search-area :drawn-areas] features))
        (-> app
            (update-in [:map :search-area] dissoc :drawn-areas-geojson :drawn-areas)))))

  DeleteDrawnArea
  (process-event [{geometry-id :geometry-id
                   entity-id :entity-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :thk.project/delete-search-geometry
           :payload {:entity-id entity-id
                     :geometry-id geometry-id}
           :result-event ->DeleteSuccess}))

  DeleteSuccess
  (process-event [{result :result} app]
    (let [info-type (info-type app)
          buffer-meters (get-in app [:map :road-buffer-meters])]
      (t/fx app
            (fetch-drawn-areas app)
            (when info-type
              (project-controller/fetch-related-info app buffer-meters info-type)))))

  InitializeCustomAreaDraw
  (process-event [_ app]
    (openlayers/enable-draw! (t/send-async! ->SaveDrawnArea))
    (assoc-in app [:map :search-area :drawing?] true))

  StopCustomAreaDraw
  (process-event [_ app]
    (openlayers/disable-draw!)
    (update-in app [:map :search-area] dissoc :drawing?))

  MouseOverDrawnAreas
  (process-event [{layer :layer
                   enter? :enter?
                   feature :feature}
                  app]
    (map-controller/update-features!
      layer
      (fn [unit]
        (let [id (.get unit "id")]
          (if (and (= id (:id feature)) enter?)
            (.set unit "hover" enter?)
            (.set unit "hover" false)))))
    app)

  ChangeTab
  (process-event [{new-tab :new-tab} app]
    (let [buffer-meters (get-in app [:map :road-buffer-meters])
          app (assoc-in app [:map :search-area :tab] new-tab)
          info-type (info-type app)]
      (println "CHANGE TAB: " info-type)
      (if info-type
        (t/fx app
              (project-controller/fetch-related-info app buffer-meters info-type))
        app))))
