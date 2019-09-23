(ns teet.map.map-view
  "Common map view"
  (:require [reagent.core :as r]
            [teet.map.openlayers :as openlayers]
            [taoensso.timbre :as log]
            [teet.routes :as routes]))

(def default-extent [20 50 30 60])

;; Center of Estonia in EPSG:3857
;(def default-center [2876047.9017341174, 8124120.910366586])

;; Center of estonian coordinate system
(def default-center [516493.16 6513417.97])

(defn map-view [e! {:keys [height class] :or {height "100%"} :as opts} map-data]
  (r/with-let [current-tool (volatile! (get-in map-data [:tool]))
               current-zoom (volatile! nil)
               current-res (volatile! nil)
               on-zoom (volatile! nil)
               prev-selected-item (volatile! nil)]

    (vreset! current-tool (get-in map-data [:tool]))
    (vreset! on-zoom (get-in map-data [:on-zoom]))

    (let [{:keys [extent]} map-data]
      [:<>
       [openlayers/openlayers
        {:id "mapview"
         :width "100%"
         ;; set width/height as CSS units, must set height as pixels!
         :height height
         :unselectable "on"
         :style (merge {:user-select "none"}
                       (when (#{:bbox-select :position-select} @current-tool)
                         {:cursor "crosshair"}))
         :class class
         :extent (or extent default-extent)
         :center default-center

         ;:selection          nav/valittu-hallintayksikko
         :on-drag (fn [item event]
                    #_(log/debug "drag" item event)
                    #_(paivita-extent item event)
                    #_(t/julkaise! {:aihe :karttaa-vedetty}))
         :on-postrender (fn [e]
                          (let [old-z @current-zoom
                                new-z (some->> e openlayers/event-map openlayers/map-zoom js/Math.round)
                                new-res (some->> e openlayers/event-map openlayers/map-resolution)]
                            (when (not= old-z new-z)
                              (vreset! current-zoom new-z)
                              (vreset! current-res new-res)
                              #_(e! (map-controller/->UpdateMapInfo {:zoom new-z
                                                                   :is-zooming? true
                                                                   :resolution new-res}))

                              #_(doseq [on-zoom @on-zoom
                                      :let [e (common-controller/on-zoom on-zoom new-z)]
                                      :when e]
                                (e! e))
                              #_(e! (map-controller/->ResetIsZooming)))))
         :on-mount (fn [initialextent]
                     (log/debug "on-mount" initialextent)
                     #_(paivita-extent nil initialextent)
                     #_(e! (map-controller/->UpdateMapLayers)))
         :on-click (fn [event]
                     ;; Either on-click or on-select will trigger. We must clear selected feature in both event handlers.
                     ;; Allow clearing selected feature only if not in approach mode
                     ;;(e! (map-controller/->ClearSelectedFeature))

                     ;;(handle-tool-click e! current-tool event)
                     )

         :on-select (fn [[item & rest] event]
                      (routes/navigate! :project {:project (:map/id item)})
                      ;; Either on-click or on-select will trigger. We must clear selected feature in both event handlers.
                      ;; Allow clearing selected feature only if not in approach mode
                      ;;(e! (map-controller/->ClearSelectedFeature))

                      #_(when-not (handle-tool-click e! current-tool event)
                        (doseq [item items]
                          (let [item (dissoc item :alue)]
                            (when (seq item)
                              (when-let [event (common-controller/map-item-selected @prev-selected-item item)]
                                (vreset! prev-selected-item item)
                                (e! event)))))))

         :on-dblclick nil

         :on-dblclick-select nil #_kasittele-dblclick-select!

         :tooltip-fn (fn [geom]
                       (when-let [tt (:map/tooltip geom)]
                         ;; Returns a function for current tooltip value or nil
                         ;; if item has no tooltip specified.
                         (constantly [:div tt])))

         :geometries (merge (get-in map-data [:geometries])
                            (get-in map-data [:layers])
                            (:layers opts))

         :current-zoom (get-in map-data [:map-info])
         ;; map of geometry layer keys to control map zoom. If a key's value changes map is re-zoomed
         :zoom-to-geometries (get-in map-data [:zoom-to-geometries])

         ;; name of layer that should be the center when zooming to geometries
         :center-on-geometry (get-in map-data [:center-on-geometry])

         ;; Use this to set a buffer for extent fitting on map view. If not defined, default value is used.
         :extent-buffer (get-in map-data [:extent-buffer])

         :rotation (if (get-in map-data [:rotate?])
                     (get-in map-data [:rotation])
                     0)

         :layers [{:type :maa-amet :layer "kaart" :default true}]
         #_(vec
                  (for [layer ["BAASKAART" "MAANTEED" "pohi_vr2"]]
                    {:type :wms :url "http://kaart.maaamet.ee/wms/alus?"
                     :layer layer
                     :style ""
                     :default true}))}]])))
