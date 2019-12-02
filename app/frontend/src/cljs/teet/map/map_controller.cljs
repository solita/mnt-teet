(ns teet.map.map-controller
  "Controller for map components"
  (:require [tuck.core :as t]
            [teet.map.openlayers :as openlayers]
            [teet.log :as log]))

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
      (assoc-in app [:map :map-restrictions] formatted)))

  FetchMapLayers
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :rpc
           :endpoint         (get-in app [:config :api-url])
           :rpc              "restriction_map_selections"
           :args             {}
           :result-event     (fn [result]
                               (->MapLayersResult result))}))

  SetBackgroundLayer
  (process-event [{layer :layer} app]
    (log/info "ASETA" layer)
    (assoc-in app [:map :background-layer] layer)))

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
