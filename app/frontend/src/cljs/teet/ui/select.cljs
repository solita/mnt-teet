(ns teet.ui.select
  "Selection components"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Select MenuItem Menu Button IconButton
                                         Input InputLabel FormControl ButtonGroup
                                         CircularProgress]]
            [taoensso.timbre :as log]
            [teet.ui.icons :as icons]
            [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.localization :refer [tr]]))

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

(defonce enum-values (r/atom {}))
(defrecord SetEnumValues [attribute values]
  t/Event
  (process-event [_ app]
    (swap! enum-values assoc attribute values)
    app))

(defn select-enum
  "Select an enum value based on attribute. Automatically fetches enum values from database."
  [{:keys [e! attribute]}]
  (when-not (contains? @enum-values attribute)
    (e! (common-controller/->Query {:query :enum/values
                                    :args {:attribute attribute}
                                    :result-event (partial ->SetEnumValues attribute)})))
  (fn [{:keys [value on-change]}]
    (let [tr* #(tr [:enum %])]
      (if-let [values (@enum-values attribute)]
        [outlined-select {:label (tr [:fields attribute])
                          :value (or value :none)
                          :on-change on-change
                          :items (into [:none] (sort-by tr* values))
                          :format-item #(if (= :none %)
                                          ;; PENDING: show better placeholder
                                          [:em (tr [:common :select :empty])]
                                          (tr* %))}]
        [CircularProgress {:size 10}]))))
