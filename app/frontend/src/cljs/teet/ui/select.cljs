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
            [teet.localization :refer [tr]]
            [teet.user.user-info :as user-info]))


(defn outlined-select [{:keys [label name id items on-change value format-item show-empty-selection?
                               error required]
                        :or {format-item :label}}]
  (r/with-let [reference (r/atom nil)
               set-ref! (fn [el]
                          (reset! reference el))]
    (let [option-idx (zipmap items (range))
          change-value (fn [e]
                         (let [val (-> e .-target .-value)]
                           (if (= val "")
                             (on-change nil)
                             (on-change (nth items (-> e .-target .-value))))))]
      [FormControl {:variant :outlined
                    :style {:width "100%"}}
       [InputLabel {:html-for id
                    :ref set-ref!
                    :error (boolean error)
                    :required (boolean required)} label]
       [Select
        {:value (or (option-idx value) "")
         :name name
         :error (boolean error)
         :label-width (or (some-> @reference .-offsetWidth) 12)
         :input-props {:id id
                       :name name}
         :on-change change-value}
        (when show-empty-selection?
          (MenuItem {:value ""} [:em (tr [:common :select :empty])]))
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
  [{:keys [e! attribute required]}]
  (when-not (contains? @enum-values attribute)
    (e! (common-controller/->Query {:query :enum/values
                                    :args {:attribute attribute}
                                    :result-event (partial ->SetEnumValues attribute)})))
  (fn [{:keys [value on-change name id error]}]
    (let [tr* #(tr [:enum %])
          values (@enum-values attribute)]
      [outlined-select {:label (tr [:fields attribute])
                        :name name
                        :id id
                        :error (boolean error)
                        :value (or value :none)
                        :on-change on-change
                        :show-empty-selection? true
                        :items (sort-by tr* values)
                        :format-item tr*
                        :required required}])))

(defn select-user
  "Select user"
  [{:keys [e! value on-change label]}]
  [outlined-select {:label label
                    :value value
                    :on-change on-change
                    :items (user-info/list-user-ids)
                    :format-item (r/partial user-info/user-name-and-email e!)}])
