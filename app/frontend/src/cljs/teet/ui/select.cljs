(ns teet.ui.select
  "Selection components"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Select MenuItem Menu Button IconButton
                                         Input InputLabel FormControl ButtonGroup]]
            [taoensso.timbre :as log]
            [teet.ui.icons :as icons]
            material-ui))

(defn outlined-select [{:keys [label items on-change value format-item]
                        :or {format-item :label}}]
  (r/with-let [reference (r/atom nil)
               label-width (r/atom 0)]
    (let [option-idx (zipmap items (range))
          change-value #(on-change (nth items (-> % .-target .-value)))]
      [FormControl {:variant :outlined
                    :style {:width "100%"}}
       [InputLabel {:html-for "language-select"
                    :ref (fn [el]
                           (reset! reference el))} label]
       [Select
        {:value (option-idx value)
         :name "language"
         :label-width (or (some-> @reference .-offsetWidth) 12)
         :input-props {:id "language-select"
                       :name "language"}
         :on-change change-value}
        (doall
          (map-indexed
            (fn [i item]
              (MenuItem {:value i
                         :key i} (format-item item)))
            items))]])))

;; TODO this needs better styles and better dropdown menu
(defn select-with-action [{:keys [items item-label icon on-select width placeholder
                                  action-icon]
                           :or {action-icon [icons/content-add]}}]
  (r/with-let [anchor (r/atom nil)
               selected (r/atom nil)]
    [:<>
     [ButtonGroup {:variant :contained :color :secondary}
      [Button {:color :secondary
               :variant :contained
               :on-click #(reset! anchor (.-target %))}
       (or (some-> @selected item-label) placeholder)]
      [Button {:color :secondary
               :variant :contained
               :size "small"
               :on-click #(on-select @selected)}
       action-icon]]
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
