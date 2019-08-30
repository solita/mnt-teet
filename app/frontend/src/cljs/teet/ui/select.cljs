(ns teet.ui.select
  "Selection components"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Select MenuItem Menu Button IconButton Input InputLabel FormControl ButtonGroup]]
            [taoensso.timbre :as log]
            [teet.ui.icons :as icons]))

;; TODO this needs better styles and better dropdown menu
(defn select-with-action [{:keys [items item-label label icon on-select width]}]
  (r/with-let [anchor (r/atom nil)
               selected (r/atom {:name "Select Workflow"})]
    [:<>
     [ButtonGroup {:variant :contained :color :secondary}
      [Button {:color :secondary
               :variant :contained
               :on-click #(reset! anchor (.-target %))}
       (:name @selected)]
      [Button {:color :secondary
               :variant :contained
               :size "small"
               :on-click #(on-select @selected)}
       [icons/content-add]]]
     [Menu {:open (boolean @anchor)
            :anchorEl @anchor}
      (doall
       (map-indexed (fn [i item]
                      ^{:key i}
                      [MenuItem {:on-click (fn [_]
                                             (reset! selected item)
                                             (reset! anchor nil)) :value (str i)}
                       (item-label item)])
                    items))]]))
