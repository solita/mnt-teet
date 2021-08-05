(ns teet.ui.select
  "Selection components"
  (:require [reagent.core :as r]
            [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [tuck.core :as t]
            [teet.localization :refer [tr tr-tree tr-enum]]
            [teet.user.user-info :as user-info]
            [teet.ui.common :as common]
            [taoensso.timbre :as log]
            [teet.ui.material-ui :refer [FormControl FormControlLabel RadioGroup Radio Checkbox
                                         Popper CircularProgress Paper Divider]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.util :as util :refer [mapc]]
            ["react"]
            [teet.util.collection :as cu]
            [teet.ui.icons :as icons]
            [teet.ui.buttons :as buttons]
            [teet.ui.typography :as typography]
            [teet.ui.chip :as chip]
            [teet.util.string :as string]
            [teet.user.user-model :as user-model]))

(def select-bg-caret-down "url('data:image/svg+xml;charset=US-ASCII,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22292.4%22%20height%3D%22292.4%22%3E%3Cpath%20fill%3D%22%23005E87%22%20d%3D%22M287%2069.4a17.6%2017.6%200%200%200-13-5.4H18.4c-5%200-9.3%201.8-12.9%205.4A17.6%2017.6%200%200%200%200%2082.2c0%205%201.8%209.3%205.4%2012.9l128%20127.9c3.6%203.6%207.8%205.4%2012.8%205.4s9.2-1.8%2012.8-5.4L287%2095c3.5-3.5%205.4-7.8%205.4-12.8%200-5-1.9-9.2-5.5-12.8z%22%2F%3E%3C%2Fsvg%3E')")

(defn- primary-select-style
  [error read-only?]
  ^{:pseudo {:focus theme-colors/focus-style}}
  (merge {:-moz {:appearance :none}
          :-webkit {:appearance :none}
          :border-radius "3px"
          :appearance "none"
          :display :block
          :background-color :white
          :border (if error
                    (str "1px solid " theme-colors/error)
                    (str "1px solid " theme-colors/black-coral-1))
          :padding "10px 30px 10px 13px"
          :width "100%"
          :max-height "41px"
          :font-size "1rem"
          :background-image select-bg-caret-down
          :background-repeat "no-repeat"
          :background-position "right .7em top 50%"
          :background-size "0.65rem auto"
          :cursor :pointer}
         (when read-only?
           {:cursor :default})))

(defn form-select [{:keys [label name id items on-change value format-item label-element
                           show-label? show-empty-selection? error error-text error-tooltip?
                           required empty-selection-label data-item? read-only? dark-theme?
                           on-focus on-blur]
                        :or {format-item :label
                             show-label? true
                             data-item? false
                             dark-theme? false}}]
  (let [option-idx (zipmap items (range))
        change-value (fn [e]
                       (let [val (-> e .-target .-value)]
                         (if (= val "")
                           (on-change nil)
                           (on-change (nth items (int val))))))
        error? (and error error-text)]
    (r/with-let [focus? (r/atom false)]
      [common/popper-tooltip (when error-tooltip?
                               ;; Show error as tooltip instead of label.
                               {:title error-text
                                :variant :error
                                ;; Always add the tootip and wrapper elements, but hide them when
                                ;; there is no error. This way the input does not lose focus when the
                                ;; tooltip is added/removed.
                                :hidden? (not error?)
                                ;; We don't want the tooltip wrapper to participate in sequential
                                ;; keyboard navigation. Otherwise we would need to hit tab twice to
                                ;; reach the input element in the wrapper.
                                :tabIndex -1
                                ;; If the input is focused, show the popup even if not hovering.
                                :force-open? @focus?})
       [:label {:for id
                :class (<class common-styles/input-label-style read-only? dark-theme?)}
        (when show-label?
          (if label-element
            [label-element label (when required [common/required-astrix])]
            [typography/Text2Bold
             label (when required
                     [common/required-astrix])]))
        [:div {:style {:position :relative}}
         [:select
          {:value (or (option-idx value) "")
           :on-focus (juxt (or on-focus identity)
                           #(reset! focus? true))
           :on-blur (juxt (or on-blur identity)
                          #(reset! focus? false))
           :name name
           :disabled read-only?
           :class (<class primary-select-style error read-only?)
           :required (boolean required)
           :id (str "links-type-select" id)
           :on-change (fn [e]
                        (change-value e))}
          (when show-empty-selection?
            [:option {:value "" :label empty-selection-label}])
          (doall
            (map-indexed
              (fn [i item]
                [:option (merge {:value i
                                 :key i}
                                (when data-item?
                                  {:data-item (str item)}))
                 (format-item item)])
              items))]]
        (when (and error? (not error-tooltip?))
          [:span {:class (<class common-styles/input-error-text-style)}
           error-text])]])))

(defn form-select-grouped [{:keys [label name id items on-change value format-item label-element
                           show-label? show-empty-selection? error error-text required empty-selection-label
                           data-item? read-only? dark-theme?]
                    :or {format-item :label
                         show-label? true
                         data-item? false
                         dark-theme? false}}]
  (let [option-idx (zipmap items (range))
        change-value (fn [e]
                       (let [val (-> e .-target .-value)
                             _ (log/debug "Group change: " val)]
                         (on-change val)))]
    [:label {:for id
             :class (<class common-styles/input-label-style read-only? dark-theme?)}
     (when show-label?
       (if label-element
         [label-element label (when required [common/required-astrix])]
         [typography/Text2Bold
          label (when required
                  [common/required-astrix])]))
     [:div {:style {:position :relative}}
      [:select
       {:value (do (log/debug "Group value: " value) (or value ""))
        :name name
        :disabled read-only?
        :class (<class primary-select-style error read-only?)
        :required (boolean required)
        :id (str "grouped-select" id)
        :on-change (fn [e]
                     (change-value e))}
       (when show-empty-selection?
         [:option {:value "" :label empty-selection-label}])
       (doall
         (map-indexed
           (fn [k group]
             [:optgroup {:label (:group-label group) :key k}
             (map-indexed (fn [i item] [:option (merge {:value (:item-value item)
                              :key (+ (* k 100) i)}
                             (when data-item?
                               {:data-item (str item)}))
              (format-item item)]) (:group-items group))])
           items))]]
     (when (and error-text error)
       [:span {:class (<class common-styles/input-error-text-style)}
        error-text])]))

;; TODO this needs better styles and better dropdown menu

(defonce enum-values (r/atom {}))
(defrecord SetEnumValues [attribute values]
  t/Event
  (process-event [_ app]
    ;; (log/debug "SetEnumValues" attribute (count values))
    (swap! enum-values assoc attribute values)
    app))

;; (common-controller/register-init-event! :set-enum-values (partial ->SetEnumValues))

(defn select-style
  []
  ^{:pseudo {:hover {:border-bottom (str "2px solid " theme-colors/primary)
                     :padding-bottom "5px"}}}               ;;This is done because material ui select can't have box sizing border box
  {:color theme-colors/blue
   :font-family "Roboto"
   :border "none"})

(defn select-opt
  []
  {:font-family "Roboto"
   :color theme-colors/gray-dark})


(defn select-with-action-styles
  []
  ^{:pseudo {:focus theme-colors/focus-style
             :invalid {:box-shadow :inherit
                       :outline :inherit}
             :hover {:margin-bottom "1px"
                     :border-bottom (str "1px solid " theme-colors/primary)}}}
  {:-moz {:appearance :none}
   :-webkit {:appearance :none}
   :cursor :pointer
   :background-color :white
   :border-radius 0
   :display :block
   :border :none
   :padding-right "2rem"
   :font-size "1rem"
   :background-image select-bg-caret-down
   :background-repeat "no-repeat"
   :background-position "right .7em top 50%"
   :background-size "0.65rem auto"
   :margin-bottom "2px"})

(defn checkbox [{:keys [value on-change label label-placement disabled size] :or {label-placement :end
                                                                                  disabled false
                                                                                  size :medium}}]
  [FormControlLabel {:label label
                     :label-placement label-placement
                     :disabled (boolean disabled)
                     :control (r/as-element [Checkbox {:checked (boolean value)
                                                       :size size
                                                       :disabled (boolean disabled)
                                                       :on-change #(let [checked? (-> % .-target .-checked)]
                                                                     (on-change checked?))}])}])

(defn select-with-action
  [{:keys [label id name value items format-item on-change
           required? show-empty-selection? empty-selection-label
           container-class class label-element show-label?]
    :or {format-item :label
         show-label? true}}]
  (let [label-element (if label-element
                        label-element
                        :span)
        option-idx (zipmap items (range))
        change-value (fn [e]
                       (let [val (-> e .-target .-value)]
                         (if (= val "")
                           (on-change nil)
                           (on-change (nth items (int val))))))]
    [:div {:class container-class}
     [:label {:html-for id}
      (when (and label show-label?)
        [label-element (str label ":")])
      [:select
       {:value (or (option-idx value) "")
        :name name
        :required (boolean required?)
        :id id
        :on-change (fn [e]
                     (change-value e))
        :class (herb/join (<class select-with-action-styles)
                          (when class
                            class))}
       (when show-empty-selection?
         [:option {:value ""
                   :label empty-selection-label
                   :class (<class select-opt)}])
       (doall
         (map-indexed
           (fn [i item]
             [:option {:value i
                       :key i
                       :class (<class select-opt)}
              (format-item item)])
           items))]]]))

(defn query-enums-for-attribute!
  ([attribute] (query-enums-for-attribute! attribute :teet))
  ([attribute database]
   (common-controller/->Query {:query :enum/values
                               :args {:attribute attribute
                                      :database database}
                               :result-event (partial ->SetEnumValues attribute)})))

(defn valid-enums-for
  "called along the lines of (valid-enums-for :document.category/project-doc ...)"
  [valid-for-criterion attribute]
  (into []
        (comp (if valid-for-criterion
                (do
                  (log/debug "valid check(2): filter " valid-for-criterion "vs attr" attribute "enum-vals" (map :db/ident  (@enum-values attribute)))
                  (filter #(= valid-for-criterion (:enum/valid-for %))))
                identity)
              (map :db/ident)) (@enum-values attribute)))

(defn with-enum-values
  "Call component with the values of the given enumeration.
  Automatically fetches enum values from database, if they haven't already been fetched."
  [{:keys [e! attribute]} _]
  (when-not (contains? @enum-values attribute)
    (log/debug "getting enum vals for attribute" attribute)
    (e! (query-enums-for-attribute! attribute)))
  (fn [{:keys [attribute]} component]
    (when-let [values (@enum-values attribute)]
      (if (vector? component)
        (conj component values)
        [component values]))))

(defn select-enum
  "Select an enum value based on attribute. Automatically fetches enum values from database."
  [{:keys [e! attribute database required label-element sort-fn]
    :or {database :teet}}]
  (when-not (contains? @enum-values attribute)
    (e! (query-enums-for-attribute! attribute database)))
  (fn [{:keys [value label on-change name show-label? show-empty-selection?
               tiny-select? id error container-class class values-filter
               full-value? empty-selection-label read-only? dark-theme?
               format-enum-fn]
        :enum/keys [valid-for]
        :or {show-label? true
             show-empty-selection? true}}]
    (let [tr* (if format-enum-fn
                (format-enum-fn (@enum-values attribute))
                #(tr [:enum %]))
          value (if (and (map? value)
                         (contains? value :db/ident))
                  ;; If value is a enum ref pulled from db, extract the kw value
                  (:db/ident value)
                  value)
          select-comp (if tiny-select?
                        select-with-action
                        form-select)
          ;; values (valid-enums-for valid-for attribute)
          values (into []
                       (comp (if valid-for
                               (do
                                 (log/debug "valid check(1): filter " valid-for "vs attr" attribute "enum vals" (map :db/ident  (@enum-values attribute)))
                                 (filter #(= valid-for (:enum/valid-for %))))
                               identity)
                             (map :db/ident))
                       (@enum-values attribute))
          values (if values-filter
                   (filterv values-filter values)
                   ;; else
                   values)]
      [select-comp {:label (or label (tr [:fields attribute]))
                    :name name
                    :id id
                    :read-only? read-only?
                    :label-element label-element
                    :empty-selection-label empty-selection-label
                    :container-class container-class
                    :show-label? show-label?
                    :error (boolean error)
                    :value (or value :none)
                    :on-change (if full-value?
                                 ;; If full value is specified, use the enum map (which may have
                                 ;; other interesting attributes) as the value instead of just
                                 ;; the keyword.
                                 (fn [kw]
                                   (on-change (some #(when (= (:db/ident %) kw) %) (@enum-values attribute))))

                                 ;; Otherwise pass the kw as value as is
                                 on-change)
                    :show-empty-selection? show-empty-selection?
                    :items (sort-by (or sort-fn tr*) values)
                    :dark-theme? dark-theme?
                    :format-item tr*
                    :required required
                    :class class
                    :data-item? true}])))

(def ^:private selectable-users (r/atom nil))

(defrecord SetSelectableUsers [users]
  t/Event
  (process-event [_ app]
    (reset! selectable-users users)
    app))

(defrecord CompleteSearchResult [callback result]
  t/Event
  (process-event [_ app]
    (callback result)
    app))

(defrecord CompleteUser [search callback]
  t/Event
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :query
           :query :user/list
           :args {:search search}
           :result-event (partial ->CompleteSearchResult callback)})))

(defrecord CompleteSearch [query callback]
  t/Event
  (process-event [_ app]
    (t/fx app
          (merge {:tuck.effect/type :query
                  :result-event (partial ->CompleteSearchResult callback)}
                 (select-keys query [:query :args])))))


(defn- format-user [{:user/keys [family-name person-id] :as user}]
  (if family-name
    (user-info/user-name user)
    (str person-id)))

(defn- user-select-popper []
  {:padding "0.3rem"
   :overflow-y "scroll"
   :z-index 99})

(defn- user-select-entry [highlight?]
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}}}
  {:padding "0.5rem"
   :cursor :pointer
   :background-color (if highlight?
                       theme-colors/gray-lightest
                       theme-colors/white)})

(defn- after-result-entry
  []
  {:padding "0.5rem"})

(defn- arrow-navigation
  "Arrow navigation key handler for select-search results"
  [state on-change on-backspace e]
  (let [{:keys [results open? highlight]} @state
        hl-idx (and (seq results) highlight
                    (cu/find-idx #(= highlight %) results))]
    (case (.-key e)
      ;; Move highlight down
      "ArrowDown"
      (when hl-idx
        (swap! state assoc
               :highlight (nth results
                               (if (< hl-idx (dec (count results)))
                                 (inc hl-idx)
                                 0)))
        (.preventDefault e))

      ;; Move highlight up
      "ArrowUp"
      (when hl-idx
        (swap! state assoc
               :highlight (nth results
                               (if (zero? hl-idx)
                                 (dec (count results))
                                 (dec hl-idx))))
        (.preventDefault e))

      "Enter"
      (when hl-idx
        (on-change highlight)
        (swap! state assoc :open? false)
        (.preventDefault e))

      "Escape"
      (when open?
        (swap! state assoc :open? false)
        (.stopPropagation e))

      "Backspace"
      (when on-backspace
        (on-backspace e))

      nil)))

(defn select-search
  "Generic select with asynchronous database search.

  :query  function from input search text to map that specifies
          what query is to be run on the backend.
          The returned map must contain :query and :args or be a function
          that can directly return results.

          If return value of query is a function, if is called to
          return the new results.

         "
  [{:keys [e! value on-change label required error
           format-result
           show-label? after-results-action
           query placeholder no-results clear-value
           start-icon input-button-icon input-element
           query-threshold autofocus? on-backspace]
    :or {show-label? true
         autofocus? false
         query-threshold 2
         placeholder (tr [:user :autocomplete :placeholder])
         no-results (tr [:user :autocomplete :no-options])
         start-icon icons/action-search
         input-button-icon icons/content-clear
         input-element TextField}
    :as opts}]
  (r/with-let [state (r/atom {:loading? false
                              :results nil
                              :open? false
                              :input ""})
               input-ref (atom nil)
               set-ref! #(do
                           (reset! input-ref %)
                           (when (and % autofocus?) (.focus %)))]
    (let [{:keys [loading? results open? input highlight]} @state
          current-input-ref (or (:input-ref opts) @input-ref)
          on-key-down (partial
                       arrow-navigation state on-change
                       (fn backspace-handler [e]
                         (if on-backspace
                           ;; If backspace handler given, call it with
                           ;; event and the input text
                           (on-backspace e input)
                           ;; When input contains the current selected value and user presses
                           ;; backspace, remove the selected value and clear input.
                           (when (and value current-input-ref
                                      (= (format-result value) (.-value current-input-ref)))
                             (on-change clear-value)
                             (swap! state assoc :input "")
                             (.preventDefault e)
                             (.stopPropagation e)))))
          load! (fn [text]
                  (let [result-fn-or-query-map (query text)]
                    (if (fn? result-fn-or-query-map)
                      (let [results (result-fn-or-query-map)]
                        (swap! state merge {:loading? false
                                            :open? true
                                            :results results
                                            :highlight (first results)}))
                      (e! (->CompleteSearch
                           result-fn-or-query-map
                           (fn [results]
                             (swap! state
                                    (fn [state]
                                      (if-not (= text (:input state))
                                        (do
                                          (log/debug "Stale result for:" text)
                                          state)
                                        (assoc state
                                               :loading? false
                                               :open? true
                                               :results results
                                               :highlight (first results)))))))))))]
      [:<>
       [input-element
        (merge {:ref set-ref!
                :label label
                :hide-label? (not show-label?)
                :required required
                :error error
                :placeholder placeholder
                :on-key-down on-key-down

                :on-blur #(js/setTimeout
                           ;; Delay closing because we might be blurring
                           ;; because user clicked one of the options
                           (fn [] (swap! state assoc :open? false))
                           200)
                :value (if value
                         (format-result value)
                         input)
                :on-focus #(if (and (empty? results)
                                    (zero? query-threshold))
                             ;; If query threshold is zero, search immediately
                             ;; even if user hasn't typed anything
                             (load! input)

                             (when (and (seq results)
                                        (>= (count input) query-threshold))
                               (swap! state assoc :open? true)))
                :on-change (fn [e]
                             (let [t (-> e .-target .-value)
                                   loading? (>= (count t) query-threshold)]
                               (when value
                                 (on-change nil))

                               (swap! state
                                      #(assoc %
                                              :input t
                                              :open? loading?
                                              :loading? loading?))

                               (when loading?
                                 (load! t))))
                :input-button-click #(do
                                       (on-change clear-value)
                                       (swap! state assoc :input "")
                                       (r/after-render
                                        (fn []
                                          (.focus @input-ref))))}
               (when start-icon
                 {:start-icon icons/action-search})
               (when input-button-icon
                 {:input-button-icon input-button-icon})
               (when-let [ic (:input-class opts)]
                 {:class ic}))]
       (when open?
         [Popper {:open true
                  :anchorEl current-input-ref
                  :placement "bottom"
                  :modifiers #js {:hide #js {:enabled false}
                                  :preventOverflow #js {:enabled false}}

                  :style {:z-index 9999} ; Must have high z-index to use in modals
                  }
          [Paper {:style {:width (.-clientWidth current-input-ref) :max-height 300}
                  :class ["user-select-popper" (<class user-select-popper)]}
           (if loading?
             [CircularProgress {:size 20}]
             [:div.select-user-list
              (if (seq results)
                (mapc (fn [result]
                          [:div.select-user-entry
                           {:class [(if (= result highlight)
                                      "active"
                                      "inactive")
                                    (<class user-select-entry (= result highlight))]
                            :on-click #(do
                                         (swap! state assoc :open? false)
                                         (on-change result))}
                           (format-result result)])
                        results)
                [:p.select-user-no-results {:style {:padding "0.5rem"}}
                 no-results])
              (when-let [{:keys [title on-click icon]} after-results-action]
                [:<>
                 [Divider]
                 [:div {:class (<class after-result-entry)}
                  [buttons/link-button
                   {:on-click on-click
                    :style {:display :flex
                            :align-items :center}}
                   icon title]]])])]])])))

(defn- selected-item-chip [{:keys [format-result format-result-chip on-change value]
                            :or {format-result str}} item]
  [chip/selected-item-chip {:on-remove #(on-change (disj (or value #{}) item))}
   ((or format-result-chip format-result) item)])

(defn- multiselect-input-wrapper-style
  [error?]
  ^{:pseudo {:focus-within theme-colors/focus-style}
    :combinators {[:> :input] {:font-size "14px"
                               :height "30px"
                               :display "inline-block"
                               :box-sizing "border-box"
                               :min-width "100px"
                               :flex-grow 1
                               :border 0
                               :margin 0
                               :outline 0}}}
  {:width "100%"
   :border (str "1px solid " (if error?
                               theme-colors/error
                               theme-colors/black-coral-1))
   :background-color theme-colors/white
   :border-radius "4px"
   :padding "5px"
   :display "flex"
   :flex-wrap "wrap"})

(defn search-result-with-checkbox
  [{:keys [value format-result] :as opts} item]
  [:div
   [Checkbox {:checked ((or value #{}) item)
              :size "small"
              :style {:padding "0"
                      :margin "0 1rem 0 0"}
              :on-change :ignore}]
   (format-result item)])

(defn select-search-multiple
  "Multiple select with select-search. Contains a list of chips for results.
  Value is a set of selected items."
  [{:keys [on-change checkbox? value label required id style
           read-only? dark-theme? label-element hide-label? error error-text] :as opts}]
  (r/with-let [input-ref (r/atom nil)
               set-input-ref! #(reset! input-ref %)]
    [:label {:for id
             :class (<class common-styles/input-label-style read-only? dark-theme?)
             :style style}
     (when-not hide-label?
       (if label-element
         [label-element label (when required [common/required-astrix])]
         [typography/Text2Bold
          label (when required
                  [common/required-astrix])]))
     [:div.select-search-multiple {:class (<class multiselect-input-wrapper-style error)
                                   :ref set-input-ref!}
      (mapc (r/partial selected-item-chip opts) value)
      ^{:key (str (count value))}                           ; remount search to clear it's text search after every change
      [select-search
       (merge (dissoc opts :hide-label? :dark-theme? :label-element :checkbox? :required)
              {:input-element :input
               :input-ref @input-ref
               :show-label? false
               :value nil
               :on-change #(on-change (conj (or value #{}) %))
               :on-backspace (fn select-search-multiple-backspace
                               [_e text]
                               (when (and (seq value)
                                          (zero? (count text)))
                                 ;; remove last value when backspacing
                                 (on-change (disj value (last value)))))}
              (when checkbox?
                {:format-result (partial search-result-with-checkbox opts)
                 :on-change #(on-change (cu/toggle value %))}))]]
     (when (and error error-text)
       [:span {:class (<class common-styles/input-error-text-style)}
        error-text])]))


(defn user-search-select-result
  [user]
  [:div
   [typography/Text {:class (herb/join (<class common-styles/margin-right 0.25)
                                       (<class common-styles/inline-block))}
    (user-model/user-name user)]
   [typography/Text3 {:class (<class common-styles/inline-block)}
    (:user/person-id user)]])

(defn select-user
  "Select user"
  [{:keys [e! value on-change label required error
           show-label? after-results-action]
    :or {show-label? true}}]
  (r/with-let [state (r/atom {:loading? false
                              :users nil
                              :open? false
                              :input ""})
               input-ref (atom nil)]
    (let [{:keys [loading? users open? input highlight]} @state]
      [:<>
       [TextField {:ref #(reset! input-ref %)
                   :id "select-user"
                   :label label
                   :show-label? show-label?
                   :required required
                   :error error
                   :placeholder (tr [:user :autocomplete :placeholder])
                   :on-key-down (fn [e]
                                  (let [hl-idx (and (seq users) highlight
                                                    (cu/find-idx #(= highlight %) users))]
                                    (case (.-key e)
                                      ;; Move highlight down
                                      "ArrowDown"
                                      (when hl-idx
                                        (swap! state assoc
                                               :highlight (nth users
                                                               (if (< hl-idx (dec (count users)))
                                                                 (inc hl-idx)
                                                                 0)))
                                        (.preventDefault e))

                                      ;; Move highlight up
                                      "ArrowUp"
                                      (when hl-idx
                                        (swap! state assoc
                                               :highlight (nth users
                                                               (if (zero? hl-idx)
                                                                 (dec (count users))
                                                                 (dec hl-idx))))
                                        (.preventDefault e))

                                      "Enter"
                                      (when hl-idx
                                        (on-change highlight)
                                        (swap! state assoc :open? false)
                                        (.preventDefault e))

                                      "Escape"
                                      (when open?
                                        (swap! state assoc :open? false)
                                        (.stopPropagation e))

                                      nil)))
                   :on-blur #(js/setTimeout
                              ;; Delay closing because we might be blurring
                              ;; because user clicked one of the options
                              (fn [] (swap! state assoc :open? false))
                              200)
                   :value (if value
                            (format-user value)
                            input)
                   :on-focus #(when (and (seq users) (>= (count input) 2))
                                (swap! state assoc :open? true))
                   :on-change (fn [e]
                                (let [t (-> e .-target .-value)
                                      loading? (>= (count t) 2)]
                                  (when value
                                    (on-change nil))

                                  (swap! state
                                         #(assoc %
                                            :input t
                                            :open? loading?
                                            :loading? loading?))

                                  (when loading?
                                    (e! (->CompleteUser t
                                                        (fn [users]
                                                          (swap! state assoc
                                                                 :loading? false
                                                                 :open? true
                                                                 :users users
                                                                 :highlight (first users))))))))
                   :input-button-click #(do
                                          (on-change nil)
                                          (swap! state assoc :input "")
                                          (r/after-render
                                            (fn []
                                              (.focus @input-ref))))
                   :input-button-icon icons/content-clear}]
       (when open?
         [Popper {:open true
                  :anchorEl @input-ref
                  :placement "bottom"
                  :modifiers #js {:hide #js {:enabled false}
                                  :preventOverflow #js {:enabled false}}

                  :style {:z-index 9999} ; Must have high z-index to use in modals
                  }
          [Paper  {:style {:width (.-clientWidth @input-ref)}
                   :class ["user-select-popper" (<class user-select-popper)]}
           (if loading?
             [CircularProgress {:size 20}]
             [:div.select-user-list
              (if (seq users)
                (mapc (fn [user]
                          [:div.select-user-entry
                           {:class [(if (= user highlight)
                                      "active"
                                      "inactive")
                                    (<class user-select-entry (= user highlight))]
                            :on-click #(do
                                         (swap! state assoc :open? false)
                                         (on-change user))}
                           (format-user user)])
                        users)
                [:span.select-user-no-results {:style {:padding "0.5rem"}} (tr [:user :autocomplete :no-options])])
              (when-let [{:keys [title on-click]} after-results-action]
                [:<>
                 [Divider]
                 [:div {:class (<class after-result-entry)}
                  [buttons/link-button
                   {:on-click on-click} title]]])])]])])))

(defn radio [{:keys [value items format-item on-change]}]
  (let [item->value (zipmap items (map str (range)))]
    [FormControl {:component "fieldset"}
     [RadioGroup {:value (item->value value)
                  :on-change #(on-change (nth items (-> % .-target .-value js/parseInt)))}
      (util/with-keys
        (for [item items
              :let [value (item->value item)
                    label ((or format-item str) item)]]
          [FormControlLabel {:label label
                             :value value
                             :control (r/as-element [Radio {:value value}])}]))]]))

(defn country-select
  [opts]
  [:div
   [form-select (merge
                  opts
                  {:format-item #(tr [:countries %])
                   :items (->> (tr-tree [:countries])
                               keys
                               (sort-by #(tr [:countries %])))})]])

(defn select-user-roles-for-contract
  [{:keys [e!] :as _opts}]
  (when-not (contains? @enum-values :company-contract-employee/role)
    (e! (query-enums-for-attribute! :company-contract-employee/role)))
  (fn [opts]
    (let [values (@enum-values :company-contract-employee/role)
          selected-enum-values (reduce (fn [acc item]
                                         (if (keyword? item)
                                           (conj acc (first (filter #(= (:db/ident %) item) values)))
                                           (conj acc item))) #{} (:value opts))
          select-options (merge {:query-threshold 0
                                 :format-result tr-enum
                                 :checkbox? true
                                 :value selected-enum-values
                                 :format-result-chip tr-enum
                                 :query (fn [text]
                                          #(vec
                                             (for [value values
                                                   :when (string/contains-words?
                                                           (tr-enum value)
                                                           text)]
                                               value)))}
                                (dissoc opts :value))]
      [:div
       [select-search-multiple select-options]])))
