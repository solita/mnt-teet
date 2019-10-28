(ns teet.ui.select
  "Selection components"
  (:require [reagent.core :as r]
            [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [Select MenuItem Menu Button
                                         InputLabel FormControl ButtonGroup]]
            [teet.ui.icons :as icons]
            [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.localization :refer [tr]]
            [teet.user.user-info :as user-info]))


(defn outlined-select [{:keys [label name id items on-change value format-item
                               show-empty-selection? error required show-label?]
                        :or {format-item :label
                             show-label? true}}]
  (r/with-let [reference (r/atom nil)
               set-ref! (fn [el]
                          (reset! reference el))]
    (let [option-idx (zipmap items (range))
          change-value (fn [e]
                         (let [val (-> e .-target .-value)]
                           (if (= val "")
                             (on-change nil)
                             (on-change (nth items (int val))))))]
      [FormControl {:variant :outlined
                    :style {:width "100%"}
                    :required required
                    :error error}
       (when show-label?
         [InputLabel {:html-for id
                      :ref set-ref!}
          label])
       [Select
        {:value (or (option-idx value) "")
         :name name
         :native true
         :required (boolean required)
         :label-width (or (some-> @reference .-offsetWidth) 12)
         :input-props {:id id
                       :name name}
         :on-change (fn [e]
                      (change-value e))}
        (when show-empty-selection?
          [:option {:value ""}])
        (doall
          (map-indexed
            (fn [i item]
              [:option {:value i
                        :key i} (format-item item)])
            items))]])))

;; TODO this needs better styles and better dropdown menu

(defonce enum-values (r/atom {}))
(defrecord SetEnumValues [attribute values]
  t/Event
  (process-event [_ app]
    (swap! enum-values assoc attribute values)
    app))

(defn select-style
  []
  {:color theme-colors/blue
   :padding-bottom 0
   :border "none"})

(defn select-with-action
  [{:keys [label id name value items format-item error on-change
           required? show-empty-selection? show-label?
           class]
    :or {format-item :label
         show-label? true}}]
  (r/with-let [reference (r/atom nil)
               set-ref! (fn [el]
                          (reset! reference el))]
    (let [option-idx (zipmap items (range))
          change-value (fn [e]
                         (let [val (-> e .-target .-value)]
                           (if (= val "")
                             (on-change nil)
                             (on-change (nth items (int val))))))]
      [:div (merge {}
                   (when class
                     {:class class}))
       [FormControl {:variant :standard
                     :required (boolean required?)
                     :error error}
        (when show-label?
          [InputLabel {:html-for id
                       :shrink true
                       :ref set-ref!}
           (str label ":")])
        [Select
         {:value (or (option-idx value) "")
          :name name
          :native true
          :required (boolean required?)
          :label-width (or (some-> @reference .-offsetWidth) 12)
          :input-props {:id id
                        :name name}
          :on-change (fn [e]
                       (change-value e))
          :classes {:root (<class select-style)}}
         (when show-empty-selection?
           [:option {:value ""}])
         (doall
           (map-indexed
             (fn [i item]
               [:option {:value i
                         :key i}
                (format-item item)])
             items))]]])))


(defn select-enum
  "Select an enum value based on attribute. Automatically fetches enum values from database."
  [{:keys [e! attribute required tiny-select? show-label?]
    :or {show-label? true}}]
  (when-not (contains? @enum-values attribute)
    (e! (common-controller/->Query {:query :enum/values
                                    :args {:attribute attribute}
                                    :result-event (partial ->SetEnumValues attribute)})))
  (fn [{:keys [value on-change name id error class]}]
    (let [tr* #(tr [:enum %])
          select-comp (if tiny-select?
                        select-with-action
                        outlined-select)
          values (@enum-values attribute)]
      [select-comp {:label (tr [:fields attribute])
                    :name name
                    :id id
                    :show-label? show-label?
                    :error (boolean error)
                    :value (or value :none)
                    :on-change on-change
                    :show-empty-selection? true
                    :items (sort-by tr* values)
                    :format-item tr*
                    :required required
                    :class class}])))

(defn select-user
  "Select user"
  [{:keys [e! value on-change label required]}]
  [outlined-select {:label label
                    :value value
                    :required required
                    :on-change on-change
                    :show-empty-selection? true
                    :items (user-info/list-user-ids)
                    :format-item (r/partial user-info/user-name-and-email e!)}])
