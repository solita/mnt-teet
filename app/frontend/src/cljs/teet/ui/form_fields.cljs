(ns teet.ui.form-fields
  "UI components for different form fields."
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [stylefy.core :as stylefy]
            [teet.localization :as localization :refer [tr]]
            [taoensso.timbre :as log]
            [teet.ui.material-ui :refer [TextField]]
            [teet.ui.icons :as icons]))



(defmulti field
  "Create an editable form field UI component. Dispatches on `:type` keyword.
  A field must always have an `:update!` callback the component calls to update a new value."
  (fn [t _] (:type t)))

(defmulti show-value
  "Create a read-only display for a value. Dispatches on `:type` keyword.
  This is not meant to be a 'disabled' input field, but for showing a readable value.
  Default implementation shows disabled field component (this seems to be enough for most cases)."
  (fn [t _] (:type t)))

(defmethod show-value :default [opts data]
  [field
   (merge opts {:disabled? true :read-only? true})
   data])

(defmethod show-value :component [skeema data]
  (let [komponentti (:component skeema)]
    [komponentti data]))

(def tooltip-icon
  "A tooltip icon that shows balloon.css tooltip on hover."
  [:div {:style {:width          16 :height 16
                 :vertical-align "middle"
                 :color          "gray"}}
   "TODO"])

(defn placeholder [{:keys [placeholder placeholder-fn row] :as field} data]
  (or placeholder
      (and placeholder-fn (placeholder-fn row))
      ""))

(defmethod field :string [{:keys [update! label name max-length min-length regex
                                  focus on-blur form? error warning table? full-width?
                                  container-style label-style input-style
                                  password? number-step? min max on-enter required?
                                  placeholder autocomplete disabled? id autofocus?
                                  after read-only?]
                           :as   field} data]
  [TextField
   (merge
    {:id        id
     :name      name
     :on-blur   on-blur
     :on-change #(let [v (-> % .-target .-value)]
                   (if regex
                     (when (re-matches regex v)
                       (update! v))
                     (update! v)))
     :value     (or data "")
     :label     label
     :required? required?
     :full-width? full-width?
     :after after
     :read-only? read-only?}
     (when max-length
       {:max-length max-length})
     (when disabled?
       {:disabled? disabled?})
     (when container-style
       {:container-style container-style})
     (when label-style
       {:label-style label-style})
     (if input-style
       {:input-style (merge {} #_style-form-fields/field-input input-style)}
       {:input-style {} #_style-form-fields/field-input})
     (when password?
       {:type "password"})
     (when number-step?
       {:type "number"})
    (when min
      {:min min})
    (when max
      {:max max})
     (when autocomplete
       {:autoComplete autocomplete})
     (when autofocus?
       {:autofocus? autofocus?})
     (when placeholder
       {:placeholder placeholder})
     (when on-enter
       {:on-key-press #(when (= "Enter" (.-key %))
                         (on-enter))}))])


(defmethod field :file-and-delete [{:keys [on-delete table-data row-number disabled? allowed-file-types] :as f} data]
  (let [row-data (get table-data row-number)]
    (if (or (empty? row-data) (:error row-data))
      [:div
      (field (assoc f :type :file
                      :error (:error row-data)))
       (when allowed-file-types
        [:span [:br] (str (tr [:form-help :allowed-file-types])  (str/join ", " allowed-file-types))])]
      [:button (merge
                        {:on-click #(on-delete row-number)}
                        (when disabled?
                          {:disabled true}))
       "TODO"])))

(defmethod field :file [{:keys [label button-label name disabled? on-change
                                error warning]
                         :as field} data]
  [:div (stylefy/use-style {} #_style-form-fields/file-button-wrapper)
   [:button (merge
              (stylefy/use-sub-style {} #_style-form-fields/file-button-wrapper :button)
              (when disabled?
                {:disabled true}))
    (if-not (empty? label) label button-label)]
   [:input
    (merge (stylefy/use-sub-style
             {} #_style-form-fields/file-button-wrapper :file-input)
           {:id "hidden-file-input"
            :type "file"
            :name name
            :on-change #(do
                          ;; Pass filename before setting target value to nil, or it will become inaccessible.
                          (on-change % (-> (aget (.-files (.-target %)) 0) .-name))
                          ;; Set file input value to nil to allow uploading a file with a same name again.
                          (aset (.-target %) "value" nil))
            ;; Hack to hide file input tooltip on different browsers.
            ;; String with space -> hide title on Chrome, FireFox and some other browsers. Not 100% reliable.
            :title " "}
           (when disabled?
             {:disabled true}))]
   (when (or error warning)
     [:div (stylefy/use-style style-base/required-element)
      (if error error warning)])])

(defmethod field :text-area [{:keys [update! label  rows cols  tooltip tooltip-length
                                     max-length on-blur  full-width?
                                     container-style style input-style label-style
                                     on-enter required?
                                     disabled? id]
                              :as field} data]
  [:span
   (when tooltip
     [:div {:style {:padding-top "10px"}}
      [:span (stylefy/use-style {} #_style-form-fields/compensatory-label) label]
      (r/as-element [tooltip-icon {:text tooltip :len (or tooltip-length "medium")}])])
   [:div (stylefy/use-style container-style)
    (when label
      [:div.label (stylefy/use-style (merge {} #_style-form-fields/form-field-label
                                          label-style))
       [label-with-required label label-style required?]])
    [:textarea (stylefy/use-style
                 (merge input-style {} #_style-form-fields/field-input
                        (when full-width?
                          {:width "100%"}))

                 (merge
                   {:on-change #(let [v (-> % .-target .-value)]
                                  (update! v))
                    :value data
                    :on-blur on-blur
                    :cols (or cols 50)
                    :rows (or rows 4)}
                   (when on-enter
                     {:on-key-press #(when (= "Enter" (.-key %))
                                       (on-enter))})
                   (when disabled?
                     {:disabled true})
                   (when max-length
                     {:max-length max-length})))]]])

(defmethod show-value :text-area [{:keys [label label-style]
                                   :as field} data]
  [:span
   [label-with-required label label-style false]
   (if (str/blank? data) "-" data)])


(def phone-regex #"\+?\d+")

(def positive-integer-regex #"^$|^\d+$")

(defmethod field :phone [opts data]
  [field (assoc opts
                :type :string
                :regex phone-regex)])

(defmethod field :number [{:keys [update! name decimals integer? number-step? min max input-style unit] :as opts}  data]
  ;; Number field contains internal state that has the current
  ;; typed in text (which may be an incompletely typed number).
  ;;
  ;; The value updated to the app model is always a parsed number.
  (let [decimals (or decimals 3)
        ;; Allow negative and positive numbers with infinite amount of optional decimals.
        valid-number-regex (re-pattern (str "^-?\\d*([\\.,]\\d*)?"))
        parse (fn [data]
                (if (:b data) (big/->float data) data))
        data (parse data)

        format-number (fn [val]
                        (if val
                          ;; Separate integer and decimal parts of the number. Limit decimal match to length defined in decimals param.
                          (let [match (re-find (re-pattern (str "(^-?\\d*)([\\.,]\\d{0," decimals "})?")) (str val))]
                            (str/replace
                              ;; Combine the number parts again, so e.g. number 1231.43324141 becomes -> 1231.433 by default.
                              (str (second match) (last match))
                              ;; Replace possible ',' characters.
                              #"," "."))
                          ""))

        state (r/atom {:value data
                       :txt (format-number data)})]
    (r/create-class
      {:display-name "form-field-number"
       :component-will-receive-props
       (fn [_ [_ _ new-value]]
         (let [new-value (parse new-value)]
           (swap! state
                  (fn [{:keys [value] :as state}]
                    (if (not= value new-value)
                      {:value new-value
                       :txt (format-number new-value)}
                      state)))))
       :reagent-render
       (fn [{:keys [update! integer? number-step? input-style unit] :as opts} _]
         (field (assoc opts
                       :type :string
                       :number-step? number-step?
                       :container-style style-base/inline-block
                       :input-style input-style
                       :min min
                       :max max
                       :regex (if integer?
                                positive-integer-regex
                                valid-number-regex)
                       :update! #(let [new-value (if (str/blank? %)
                                                   nil
                                                   (format-number %))
                                       parsed-float (and new-value (js/parseFloat new-value))]
                                   (reset! state {:value parsed-float
                                                  :txt new-value})
                                   (update! parsed-float))
                       :after (when unit [:span {:style {:margin-left "0.5em"}} unit]))
                (:txt @state)))})))

(defmethod field :number-range
  ;;  "Defines a UI component to select a number range which can be open at either end"
  [{:keys [update! label show-label? name on-enter integer?]}
   data]
  [:span
   (when (or (nil? show-label?)
             (true? show-label?))
     [:div (stylefy/use-style {} #_style-form-fields/form-field-label)
      (or label (tr [:fields :common :number-range]))])
   [:div
    [field {:type :number
            :name (keyword (str "start-number-field-" name))
            :input-style {} #_style-form-fields/date-field-input
            :on-enter on-enter :integer? integer?
            :update! (fn [new-value]
                       (update! (assoc data :start-number new-value)))}
     (:start-number data)]
    [:span " \u2014 "]
    [field {:type :number
            :name (keyword (str "end-number-field-" name))
            :input-style {} #_style-form-fields/date-field-input
            :on-enter on-enter :integer? integer?
            :update! (fn [new-value]
                       (update! (assoc data :end-number new-value)))}
     (:end-number data)]]])

;; Matches empty or any valid hour (0 (or 00) - 23)
(def hour-regex #"^(^$|0?[0-9]|1[0-9]|2[0-3])$")

(def unrestricted-hour-regex #"\d*")

;; Matches empty or any valid minute (0 (or 00) - 59)
(def minute-regex #"^(^$|0?[0-9]|[1-5][0-9])$")

;; Validate allowed characters to date picker, e.g. 22.09.1982
(def +date-regex+ #"\d{0,2}((\.\d{0,2})(\.[1-2]{0,1}\d{0,3})?)?")

(defmethod field :time [{:keys [update! label] :as opts} data]
  [TextField
   {:type "time"
    :value data
    :on-change #(update! (-> % .-target .-value))}])

(defmethod field :default [opts data]
  [:div.error "Missing field type: " (:type opts)])


(defmethod field :table [{:keys [table-fields table-wrapper-style update! delete?
                                 add-label add-label-disabled? error-data id
                                 field-labels? cell-style label label-style] :as opts} data]
  (let [data (if (empty? data)
               ;; table always contains at least one row
               [{}]
               data)]

    [:div
     (when label
       [label-with-required label label-style false])
     [:table (stylefy/use-style style-base/table)
      [:thead
       [:tr
        (for [{:keys [name label width tooltip tooltip-pos tooltip-len]} table-fields]
          ^{:key name}
          [:th {:style
                (merge {:width width :white-space "pre-wrap"}
                       style-base/table-header-cell
                       (when-not label {:padding "0px"}))}
           label
           (when tooltip
             [tooltip-icon {:text tooltip :pos  tooltip-pos :len tooltip-len}])])
        (when delete?
          [:th " "])]]

      [:tbody
       (doall
        (map-indexed
         (fn [i row]
           (let [{:keys [errors missing-required-fields]} (and error-data
                                                               (< i (count error-data))
                                                               (nth error-data i))]
             ^{:key i}
             [:tr (merge {:id (str "row_" i)}
                         ;; If there are errors or missing fields, make the
                         ;; row taller to show error messages
                         (when (or errors missing-required-fields)
                           {:style {:height 65}}))
              (doall
               (for [{:keys [name read write width type component] :as tf} table-fields
                     :let [field-error (get errors name)
                           missing? (get missing-required-fields name)
                           update-fn (if write
                                       #(update data i write %)
                                       #(assoc-in data [i name] %))
                           value ((or read name) row)]]
                 ^{:key name}
                 [:td {:style (merge style-base/table-header-cell
                                     {:width width}
                                     cell-style)}
                  (if (= :component type)
                    (component {:update-form! #(update! (update-fn %))
                                :table? true
                                :row-number i
                                :data value})
                    [field (merge (assoc (if field-labels?
                                           tf
                                           (dissoc tf :label))
                                         :table? true
                                         :row-number i
                                         :table-data data
                                         :update! #(update! (update-fn %)))
                                  (when missing?
                                    {:warning (tr [:common-texts :required-field])})
                                  (when field-error
                                    {:error field-error}))
                     value])]))
              (when delete?
                [:td
                 [buttons/button {:type :icon
                                  :icon icons/content-clear
                                  :label ""
                                  :on-click #(update! (vec (concat (when (pos? i)
                                                                     (take i data))
                                                                   (drop (inc i) data))))}]])]))
         data))]]
     (when add-label
       [:div (stylefy/use-style style-base/button-add-row)
        [buttons/button (merge {:type :secondary
                                :on-click #(update! (conj (or data []) {}))
                                :label add-label
                                :icon icons/content-add
                                :label-style style-base/button-label-style
                                :disabled (if add-label-disabled?
                                            (add-label-disabled? (last data))
                                            (values/effectively-empty? (last data)))}
                               (when (not (nil? id))
                                 {:id (str id "-button")}))]])]))

(defmethod field :checkbox [{:keys [update! table? label warning style extended-help disabled?]} checked?]
  [:div "TODO:checkbox"]
  #_[buttons/toggle {:label label
                   :value checked?
                   :type :form-field-checkbox
                   :toggle-type :checkbox
                   :custom-style (merge {} #_style-form-fields/checkbox-field style (when disabled?
                                                                                    {:cursor "default"}))
                   :on-toggle #(update! (not checked?))
                   :disabled? disabled?}])

(defmethod show-value :checkbox [{:keys [label] :as opts} checked?]
  [:div
   (when checked?
     [icons/action-check-circle])])

(defmethod field :radio [{:keys [update! table? label warning style extended-help disabled?]} checked?]
  [:div "FIXME:radio"]
  #_[buttons/toggle {:label label
                   :value checked?
                   :type :form-field-checkbox
                   :toggle-type :radio
                   :custom-style (merge {} #_style-form-fields/checkbox-field style)
                   :on-toggle #(update! (not checked?))
                   :disabled? disabled?}])

(defmethod field :range [{:keys [update! min max width]} data]
  [:div "FIXME:range"]
  #_[:input {:type "range"
           :min min
           :max max
           :value data
           :style {:width width
                   :margin-right "8px"}
           :on-change #(update! (-> % .-target .-value js/parseInt))}])


#_(defmethod field :checkbox-group [{:keys
                                   [update! table? label show-option options
                                    help error warning  option-enabled? option-addition
                                    checkbox-group-style use-label-width? variant required?]} data]
  ;; Options:
  ;; :header? Show or hide the header element above the checkbox-group. Default: true.
  ;; :option-enabled? Is option checkable. Default: true
  ;; option-addition is a map, that knows which option needs additions and the addition. e.g. {:value: :other :addition [ReagentObject]}
  (let [selected (set (or data #{}))
        option-enabled? (or option-enabled? (constantly true))
        label-style (if use-label-width? style-base/checkbox-label-with-width style-base/checkbox-label)]
    [:div.checkbox-group {:style (if checkbox-group-style checkbox-group-style {})}
     (when label
       [label-with-required label {} required?])
     [:div (if (= :horizontal variant) {:style {:display "flex"}})
      (doall
       (map-indexed
         (fn [i option]
           (let [checked? (boolean (selected option))
                 is-addition-valid (and (not (nil? option-addition)) (= option (:value option-addition)) checked?)
                 addition (when is-addition-valid (:addition option-addition))]
             ^{:key i}
             [:div {:style {:display "flex" :padding-bottom "8px"}}
               [buttons/toggle {:id (str i "_" (str option))
                             :label      (when-not table? (show-option option))
                             :value      checked?
                             :disabled   (not (option-enabled? option))
                             :labelStyle (merge label-style
                                                (if (not (option-enabled? option))
                                                  style-base/disabled-color
                                                  {:color "rgb(33, 33, 33)"}))
                             :on-toggle #(update! ((if checked? disj conj) selected option))}]
              (when is-addition-valid
                [:span {:style {:padding-left "20px"}} addition])]))
         options))]
     (when (or error warning)
       [:div
        (theme/use-sub-style {} #_style-form-fields/radio-selection :required)
        (if error error warning)])]))

(defmethod field :date
  ;  "Defines a date field type with a calendar picker"
  [{:keys [update! name label disabled? default-date placeholder rivi opening-direction
           on-enter on-focus tooltip] :as opts} data]
  [TextField
   {:type "date"
    :value data
    :on-change #(update! (-> % .-target .-value))}])

;; A compact field containing multiple subfields
(defmethod field :multi [{:keys [update! fields label label-style required?]} data]
  [:div
   (when label
     [label-with-required label label-style required?])
   [:div (stylefy/use-style {:display "flex"
                             :flex-direction "row"})
    (doall
     (for [{:keys [name read write] :as f} fields]
       ^{:key (str name)}
       [field (assoc f
                     :update! (fn [field-val]
                                (update!
                                 (if write
                                   (write data field-val)
                                   (assoc data name field-val)))))
        ((or read name) data)]))]])

;; A key=>value mapping (table with 2 data columns)
(defmethod field :mapping [{:keys [update! key value default-key sort label label-style required?
                                   remove-button? decorator]} data]
  (assert (or (nil? data)
              (map? data)) (str ":mapping type requires map as data, got: " (pr-str data)))
  (r/with-let [id->key (atom  (let [data (if default-key
                                           (dissoc data default-key)
                                           data)]
                                (zipmap (range)
                                        (concat (map cljs.core/key
                                                     (if sort
                                                       (sort-by sort data)
                                                       data))
                                                [::new]))))]
    [:div
     (when label
       [label-with-required label label-style required?])
     [:table
      [:thead
       [:tr
        [:th (:label key)]
        [:th (:label value)]
        (when-not (false? remove-button?)
          [:th " "])
        (when decorator
          [:th " "])]]
      [:tbody
       (doall
        (for [[id k] (sort-by key @id->key)
              :let [v (get data k)]]
          ^{:key id}
          [:tr {:data-mapping-key (str k)}
           [:td [field (-> key
                           (assoc :update! (fn [new-key]
                                             ;; FIXME: prevent new-key from being the same as a previous
                                             (swap! id->key #(as-> % %
                                                               (assoc % id new-key)
                                                               (if (= ::new k)
                                                                 ;; If new key was given value, add ::new again
                                                                 (assoc % (inc id) ::new)
                                                                 %)))
                                             (-> data
                                                 (dissoc k)
                                                 (assoc new-key v)
                                                 update!)))
                           (dissoc :label)
                           (assoc :placeholder (when (= k ::new)
                                                 (tr [:common-text :new])))) k]]
           [:td [field (-> value
                           (assoc :update! (fn [new-value]
                                             (update! (assoc data k new-value))))
                           (dissoc :label)) v]]
           (when-not (false? remove-button?)
             [:td (when-not (= k ::new)
                    [buttons/button {:type :icon
                                     :icon icons/content-clear
                                     :label ""
                                     :on-click #(do
                                                  (swap! id->key dissoc id)
                                                  (update! (dissoc data k)))}])])
           (when decorator
             [:td (decorator [k v])])]))
       (when default-key
         ^{:key "default"}
         [:tr
          [:td (tr [:common-text :default])]
          [:td [field (-> value
                          (assoc :update! (fn [new-value]
                                            (update! (assoc data default-key new-value))))
                          (dissoc :label))
                (get data default-key)]]
          [:td]])]]]))

(defmethod field :number-list [{:keys [update! integer?]} data]
  [field {:type :list-of
          :remove-nil? true
          :update! update!
          :field {:type :number
                  :integer? integer?
                  :input-style {:width 50}}} data])

(defmethod field :list-of [{:keys [label label-style update! required? remove-nil?] list-field :field} data]
  (let [data (or data [])]
    (assert (vector? data) ":list-of field requires data to be a vector")
    [:div
      (when label
        [label-with-required label label-style required?])
     [:div (stylefy/use-style {:display "flex"
                               :flex-direction "row"})
      (doall
       (map-indexed
        (fn [i value]
          (let [new? (= value ::new)]
            ^{:key i}
            [field (assoc list-field
                          :update! (fn [value]
                                     (let [vals (if new?
                                                  (conj data value)
                                                  (assoc data i value))]
                                       (update!
                                        (if remove-nil?
                                          (filterv some? vals)
                                          vals)))))
             (if new? nil value)]))
        (conj data ::new)))]]))

(defmethod field :component [{:keys [update! component]} data]
  [:div.component (component {:update-form! #(do (log/info ":component update" %)
                                                 (update! %))
                              :data data})])


(defn- color-indicator [on-click color]
  [:div.color-indicator
   (merge (when on-click
            {:on-click on-click})
          (stylefy/use-style (merge
                        {:border "solid 1px black"
                         :width 20 :height 20
                         :display "inline-block"
                         :margin-right "0.5em"
                         :background-color color
                         :position "relative"
                         :margin-left "1em"
                         :top 7}
                        (when on-click
                          {:cursor "pointer"}))))])
