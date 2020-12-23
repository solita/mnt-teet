(ns teet.map.map-overlay
  "Utility UI code for map overlays"
  (:require [clojure.string :as str]
            [goog.object :as gobj]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.common.common-styles :as common-styles]
            [teet.map.map-styles :as map-styles]
            [teet.ui.common :as common]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [IconButton]]))

;; Atom that on-select handlers can use to set overlay
(defonce selected-item-overlay (r/atom nil))

(defn overlay
  "Helper for creating map overlay components"
  [{:keys [arrow-direction width height single-line? style
           background-color
           on-close]
    :or {height 60
         single-line? true
         background-color map-styles/overlay-background-color}} content]
  [:div {:class (<class map-styles/map-overlay-container width height arrow-direction background-color)
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
            [common/Link {:target :_blank
                          :href val
                          :class (<class common-styles/white-link-style false)} val]
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
