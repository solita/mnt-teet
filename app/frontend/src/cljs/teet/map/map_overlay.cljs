(ns teet.map.map-overlay
  "Utility UI code for map overlays"
  (:require [teet.map.map-styles :as map-styles]
            [herb.core :refer [<class]]
            [goog.object :as gobj]
            [reagent.core :as r]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [IconButton Link]]
            [clojure.string :as str]
            [teet.ui.icons :as icons]))

;; Atom that on-select handlers can use to set overlay
(defonce selected-item-overlay (r/atom nil))

(defn overlay
  "Helper for creating map overlay components"
  [{:keys [arrow-direction width height single-line? style
           on-close]
    :or {height 60
         single-line? true}} content]
  [:div {:class (<class map-styles/map-overlay-container width height arrow-direction)
         :style (or style {})}

   (when arrow-direction
     [:div {:class (<class map-styles/map-overlay-arrow width height arrow-direction)}])
   [:div {:class (<class map-styles/map-overlay-content single-line?)}
    content]
   (when on-close
     [IconButton {:on-click on-close}
      [icons/navigation-close]])])

(defn feature-info-overlay [overlay-options feature]
  (let [properties (.getProperties feature)]
    [overlay
     overlay-options
     [:span
      [itemlist/ItemList {}
       (for [key (sort (gobj/getAllPropertyNames properties))
             :when (not= key "geometry")
             :let [val (str (aget properties key))]
             :when (not (str/blank? val))]
         ^{:key key}
         [itemlist/Item {:label key}
          (if (= key "url")
            [Link {:target :_blank
                   :href val} val]
            val)])]]]))

(defn feature-info-on-select
  "Map on-select callback that shows feature info overlay.
  Provide the overlay options with partial application"
  [overlay-options {f :map/feature :as g}]
  (js/console.log "WFS feature: " f)
  (reset! selected-item-overlay
          {:coordinate (-> f .getGeometry .getFirstCoordinate)
           :content [feature-info-overlay (merge overlay-options
                                                 {:on-close #(reset! selected-item-overlay nil)})
                     f]}))
