(ns teet.map.map-controller
  "Controller for map components"
  (:require [tuck.core :as t]))

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
                         layers))))))

(extend-protocol t/Event
  ToggleMapControls
  (process-event [_ app]
    (update-in app [:map :map-controls :open?] not)))

(extend-protocol t/Event
  CloseMapControls
  (process-event [_ app]
    (assoc-in app [:map :map-controls :open?] false)))

(extend-protocol t/Event
  ToggleCategoryCollapse
  (process-event [{:keys [category]} app]
    (update-in app [:map :map-controls category :collapsed?] not)))


(extend-protocol t/Event
  LayerToggle
  (process-event [{:keys [category layer]} app]
    (update-in app [:map :map-restrictions category layer] not)))

(extend-protocol t/Event
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
      (assoc-in app [:map :map-restrictions] formatted))))


(extend-protocol t/Event
  FetchMapLayers
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :rpc
           :endpoint         (get-in app [:config :api-url])
           :rpc              "restriction_map_selections"
           :args             {}
           :result-event     (fn [result]
                               (->MapLayersResult result))})))
