(ns teet.asset.cost-items-map-view
  "Map view for cost items pages."
  (:require [reagent.core :as r]
            [teet.ui.context :as context]
            [teet.project.project-layers :as project-layers]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.map.openlayers.drag :as drag]
            [teet.asset.cost-items-controller :as cost-items-controller]
            [teet.common.common-controller :as common-controller]
            [teet.localization :as localization]))

(defn project-map* [{:keys [e!]
                     oid :asset/oid :as opts} {:keys [app project]}]
  (r/with-let [overlays (r/atom [])
               fitted-atom (atom false)
               set-overlays! #(when (not= @overlays %)
                                ;; Only set if actually changed (to avoid rerender loop)
                                (reset! overlays %))]
    [map-view/map-view e!
     (merge-with
      merge
      opts
      {:allow-select? false
       :overlays @overlays
       :layers (project-layers/create-layers
                {:e! e! :project project :app app :set-overlays! set-overlays!}
                (when project
                  (partial project-layers/project-road-geometry-layer
                           {:fitted-atom fitted-atom
                            :style (partial map-features/road-line-style
                                            5 "rgba(100,100,100,0.5)")}))

                ;; Add geometries for possible parent asset and other components
                (when (and (some? oid)
                           (not= "new" oid))
                  (constantly
                   {:asset-geometries
                    (map-layers/geojson-layer
                     (common-controller/query-url
                      :asset/geometries
                      {:asset/oid oid
                       :language @localization/selected-language})
                     "asset-geometries" nil
                     map-features/asset-or-component
                     {})})))})
     (:map app)]))

(defn project-map [opts]
  [context/consume :cost-items-map
   [project-map* opts]])

(defn with-map-context
  [app project child]
  [context/provide :cost-items-map {:app app :project project}
   child])


(defn location-map [{oid :asset/oid :keys [e! value]}]
  (e! (cost-items-controller/->InitMap))
  (let [current-value (atom value)
        dragging? (atom false)]
    (fn [{:keys [e! value on-change]}]
      (reset! current-value value)
      [project-map
       {:e! e!
        :asset/oid oid
        :on-click
        (fn [{c :coordinate}]
          (let [{start :location/start-point
                 end :location/end-point
                 single? :location/single-point? :as v} @current-value]
            (cond
              ;; If no start point or location is single point, set it
              (or single?
                  (nil? start)) (on-change (assoc v :location/start-point c))

              ;; If no end point, set it
              (nil? end) (on-change (assoc v :location/end-point c))

              ;; Otherwise do nothing
              :else nil)))

        :event-handlers
        (drag/drag-feature
         {:accept (comp :map/feature :geometry)
          :on-drag drag/on-drag-set-coordinates
          :on-drop
          (fn [target to]
            (when-let [p (some-> target :geometry :map/feature
                                 .getProperties (aget "start/end"))]
              (on-change
               (assoc @current-value
                      (case p
                        "start" :location/start-point
                        "end" :location/end-point)
                      to))))
          :dragging? dragging?})

        :layers
        {:selected-road-geometry
         (when-let [geojson (:location/geojson value)]
           (map-layers/geojson-data-layer
            "selected-road-geometry"
            geojson
            map-features/asset-road-line-style
            {:fit-on-load? true
             :fit-condition
             (fn [_]
               (not @dragging?))}))}}])))
