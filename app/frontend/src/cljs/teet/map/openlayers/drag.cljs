(ns teet.map.openlayers.drag
  "Drag event handling")

(defn drag-feature [{:keys [accept on-drag on-drop
                            dragging?]}]
  (let [drag-target (atom nil)]
    {"pointerdown"
     (fn [e]
       (when (accept e)
         (when dragging? (reset! dragging? true))
         (reset! drag-target e)))

     "pointerdrag"
     (fn [e]
       (when-let [target @drag-target]
         (.preventDefault (:event e))
         (on-drag target e)))

     "pointerup"
     (fn [e]
       (when-let [target @drag-target]
         (when dragging? (reset! dragging? false))
         (reset! drag-target nil)
         (on-drop target (:location e))))}))

(defn on-drag-set-coordinates
  "On-drag callback that sets coordinates to dragged position."
  [target e]
  (let [f (-> target :geometry :map/feature)]
    (-> f
        .getGeometry
        (.setCoordinates (clj->js (:location e))))))
