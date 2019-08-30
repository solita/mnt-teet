(ns teet.ui.select
  "Selection components"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Select MenuItem Menu Button IconButton Input InputLabel FormControl]]
            [taoensso.timbre :as log]))


(defn select-with-action [{:keys [items item-label label icon on-select width]}]
  (r/with-let [anchor (r/atom false)]
    [:<>
     [Button {:color :secondary
              :variant :contained
              :on-click #(reset! anchor (.-target %))}
      label]
     [Menu {:open (boolean @anchor)
            :anchorEl @anchor}
      (doall
       (map-indexed (fn [i item]
                      ^{:key i}
                      [MenuItem {:on-click #(on-select item) :value (str i)}
                       (item-label item)])
                    items))]]))
