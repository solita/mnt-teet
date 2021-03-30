(ns teet.map.openlayers.drag
  "Drag event handling")

(defn drag-feature [accept on-drag on-drop]
  (let [drag-target (atom nil)]
    {"pointerdown"
     (fn [e]
       (when (accept e)
         (reset! drag-target e)))

     "pointerdrag"
     (fn [e]
       (when-let [target @drag-target]
         (.preventDefault (:event e))
         (on-drag target e)))

     "pointerup"
     (fn [e]
       (when-let [target @drag-target]
         (reset! drag-target nil)
         (on-drop target (:location e))))}))

(defn on-drag-set-coordinates
  "On-drag callback that sets coordinates to dragged position."
  [target e]
  (let [f (-> target :geometry :map/feature)]
    (-> f
        .getGeometry
        (.setCoordinates (clj->js (:location e))))))
