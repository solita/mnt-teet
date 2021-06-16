(ns teet.ui.form
  "Common container for forms"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid Portal]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
            [goog.object :as gobj]
            [clojure.spec.alpha :as s]
            [herb.core :refer [<class]]
            [teet.ui.buttons :as buttons]
            [tuck.core :as t]
            [teet.log :as log]
            [teet.ui.context :as context]
            [clojure.set :as set]
            [teet.common.common-styles :as common-styles]
            [teet.ui.panels :as panels]
            [teet.ui.common :as common]
            [clojure.walk :as walk]
            [teet.util.collection :as cu]))

(defprotocol ToValue
  :extend-via-metadata true
  (to-value [this]
    "Return the actual form value from this internal field state."))

(defn- to-value* [x]
  (if (satisfies? ToValue x)
    (to-value x)
    x))

(extend-protocol ToValue
  cljs.core/PersistentArrayMap
  (to-value [this]
    (cu/map-vals to-value* this))

  cljs.core/PersistentHashMap
  (to-value [this]
    (cu/map-vals to-value* this))

  cljs.core/PersistentVector
  (to-value [this]
    (mapv to-value* this)))

(defonce field-specs (atom {}))

(defn- valid-attribute?
  "Validate value based on spec of kw. Caches kw spec in field-specs atom."
  [kw value]
  (if-let [spec (or (@field-specs kw)
                    ((swap! field-specs assoc kw (s/get-spec kw)) kw))]
    (not= :cljs.spec.alpha/invalid (s/conform spec value))
    true))

(defn- add-validation [field validate-attribute-fn attribute]
  (let [field-component (first field)]
    (cond
      ;; For text fields add input props to do on-blur validation
      (= TextField field-component)
      (update-in field [1 :on-change] (fn [on-change]
                                        (fn [value]
                                          (validate-attribute-fn attribute (on-change value)))))

      ;; Other fields just validate after on-change
      :else
      (update-in field [1 :on-change] (fn [on-change]
                                        (fn [value]
                                          (validate-attribute-fn attribute (on-change value))))))))

(defn- contains-predicate
  "Check that predicate matches:
   (cljs.core/fn [%] (cljs.core/contains? % ?attr))

  Returns ?attr or nil."
  [pred]
  (and (seq? pred)
       (= 3 (count pred))
       (= 3 (count (nth pred 2)))
       (= 'cljs.core/contains? (first (nth pred 2)))
       (nth (nth pred 2) 2)))

(defn- missing-attributes
  "Return missing attributes based on spec"
  [spec value]
  (into #{}
        (when spec
          (for [{:keys [pred] :as _problem} (:cljs.spec.alpha/problems (s/explain-data spec value))
                :let [attribute (contains-predicate pred)]
                :when attribute]
            attribute))))

(defn form-buttons
  ([] (form-buttons nil))
  ([justify]
   (merge
     {:display :flex
      :flex-wrap :wrap
      :justify-content :center
      :margin-top "1.5rem"
      :padding-bottom "1rem"}
    (when justify
      {:justify-content justify}))))

(defn form-footer [{:keys [delete delete-message delete-confirm-button-text delete-cancel-button-text
                           delete-title delete-disabled-error-text delete-link?
                           cancel validate disabled?]}]
  (let [delete-element
        (when delete
          [common/popper-tooltip
           (when delete-disabled-error-text
             {:title delete-disabled-error-text})
           [buttons/delete-button-with-confirm
            (merge
             {:action delete
              :modal-text delete-message
              :id "delete-button"}
             (when delete-link?
               {:trashcan? true})
             (when delete-confirm-button-text
               {:confirm-button-text delete-confirm-button-text})
             (when delete-cancel-button-text
               {:cancel-button-text delete-cancel-button-text})
             (when delete-disabled-error-text
               {:disabled true})
             (when delete-title
               {:modal-title delete-title}))
            (tr [:buttons :delete])]])]
    [:div {:class (<class form-buttons)}
     [:div
      (when-not delete-link?
        delete-element)]
     [:div {:style {:margin-left :auto
                    :text-align :center}}
      [:div {:class (<class common-styles/margin-bottom 1)}
       (when cancel
         [buttons/button-secondary {:style {:margin-right "1rem"}
                                    :disabled disabled?
                                    :class "cancel"
                                    :on-click cancel}
          (tr [:buttons :cancel])])
       (when validate
         [buttons/button-primary {:disabled disabled?
                                  :type :submit
                                  :class "submit"
                                  :on-click validate}
          (tr [:buttons :save])])]
      (when (and delete-link? delete-element)
        [:div
         delete-element])]]))

(defn- hide-field?
  "Returns true if field is nil or if it has `:step` in its metadata and
  it's different from the `current-step`"
  [current-step field]
  (or (nil? field)
      (when-let [step (-> field meta :step)]
        (not= step current-step))))

(defn update-atom-event
  "Returns a tuck event constructor that updates the given atom when processed.
  Leaves app state unaffected."
  ([the-atom] (update-atom-event the-atom (fn [_old new] new)))
  ([the-atom update-fn]
   #(reify t/Event
      (process-event [_ app]
        (swap! the-atom update-fn %)
        app))))

(defn callback-change-event
  "Returns a tuck change event constructor that invokes a callback when processed.
  Leaves app state unaffected."
  [the-callback]
  #(reify t/Event
     (process-event [_ app]
       (the-callback %)
       app)))

(defn reset-atom-event
  "Returns a tuck event constructor that resets the given atom when processed.
  Leaves app state unaffected."
  [the-atom new-value]
  #(reify t/Event
     (process-event [_ app]
       (reset! the-atom new-value)
       app)))

(defn callback-event
  "Returns a tuck event that calls the given 0-arity callback
  when processed.
  Leaves app state unaffected."
  [callback]
  #(reify t/Event
     (process-event [_ app]
       (callback)
       app)))

(defn required-field? [attribute required-fields]
  (boolean
   (if (vector? attribute)
     (not-empty (clojure.set/intersection required-fields (set attribute)))
     (required-fields attribute))))

(defn validate-attribute
  "Given custom field validation, a field (keyword or vector of keywords) and
  a value. Returns possibly empty set of invalid attribute keywords."
  [validate-field field value]
  (if (and validate-field (validate-field value))
    ;; If custom validation fails then mark all fields as invalid
    (if (vector? field)
      (set field)
      #{field})
    ;; otherwise check each attributes spec
    (reduce
     (fn [fields [field value]]
       (if (valid-attribute? field value)
         (disj fields field)
         (conj fields field)))
     #{}
     (if (vector? field)
       (zipmap field value)
       {field value}))))

(defn attribute-value
  "Return the value of the attribute in the form."
  ([form-value attribute] (attribute-value form-value attribute nil))
  ([form-value attribute not-found]
   (cond
     (keyword? attribute)
     (get form-value attribute not-found)

     (vector? attribute)
     (mapv #(get form-value % not-found) attribute)

     :else
     (throw (ex-info "Attribute must be keyword or vector of keywords"
                     {:invalid-attribute attribute
                      :form-value form-value})))))

(defn validate-form
  "Validate whole form value.
  Spec is the spec for the whole form value (eg. s/keys).
  Value is the current form value map.
  Fields is collection of fields descriptions in the form, containing
  :attribute and optional :validate function.

  Returns possibly empty set of missing or invalid attribute keywords."
  [spec value fields]
  (reduce set/union
          (missing-attributes spec value)
          (keep (fn [{:keys [attribute validate]}]
                  (let [attr-value (attribute-value value attribute ::no-value)]
                    (when (not= attr-value ::no-value)
                      (validate-attribute validate attribute attr-value))))
                fields)))

(defn validate-attribute-fn
  "Validate attribute and set the form invalid-attributes atom."
  [invalid-attributes validate-field field value]
  (swap! invalid-attributes
         (fn [fields]
           (let [before fields
                 after
                 (set/union (set/difference fields
                                            (if (vector? field)
                                              (set field)
                                              #{field}))
                            (validate-attribute validate-field field value))]
             #_(println "validate " field " from " before " => " after)
             after))))

(defn- field*
  [field-info _field
   {:keys [invalid-attributes required-fields current-fields] :as ctx}]

  (let [{:keys [attribute required?]
         validate-field :validate
         :as field-info} (if (map? field-info)
                           field-info
                           {:attribute field-info})]
    (r/create-class
      {:component-did-mount
       (fn [_]
         (swap! current-fields assoc attribute field-info))
       :component-will-unmount
       (fn [_]
         (swap! current-fields dissoc attribute))
       :reagent-render
       (fn [_ field {:keys [update-attribute-fn value]}]
         (let [value (attribute-value value attribute)
               error-text (and validate-field
                               (validate-field value))
               error? (boolean
                        (or error-text
                            (some @invalid-attributes
                                  (if (vector? attribute)
                                    attribute
                                    [attribute]))))
               opts {:value value
                     :on-change (r/partial update-attribute-fn attribute)
                     :error error?
                     :error-text error-text
                     :required (or required? (required-field? attribute required-fields))}]
           [:div {:data-form-attribute (str attribute)
                  :class (<class common-styles/margin-bottom 1)}
            (add-validation
              (update field 1 (fn [{label :label :as input-opts}]
                                (merge opts                 ;;input-opts' required overrides the computed required value because it's more specific
                                       (dissoc input-opts :value :on-change)
                                       (when-not label
                                         {:label (tr [:fields attribute])}))))
              (partial validate-attribute-fn invalid-attributes validate-field) attribute)]))})))

(defn field
  "Form component in form2. Field-info is the attribute
  this form component value is stored in (or a map containing :attribute).

  The field is a hiccup vector containing the form component.
  The current value and on-change attributes are automatically added to it."
  [field-info field]
  (assert (vector? field)
          "Field must be a hiccup vector")
  (assert (map? (second field))
          "First argument to field must be an options map")
  (context/consume
   :form
   [field* field-info field]))

(defn- change-event-value
  "Extract value for a change event.
  If value is React event, extract the target value.
  Otherwise pass value as is."
  [value]
  (if (and (not (boolean? value))
           (not (string? value))
           (not (number? value))
           (gobj/containsKey value "target"))
    (gobj/getValueByKeys value "target" "value")
    value))

(defn remove-from-many-at-index
  [e! on-change form field idx]
  (e! (on-change
        (update form
                field
                (fn [items]
                  (into (subvec items 0 idx)
                        (subvec items (inc idx))))))))

(defn- many-container [{:keys [attribute before after atleast-once?]} body
                       {update-parent-fn :update-attribute-fn
                        form-value :value :as form-ctx}]
  (let [parent-value (get form-value attribute)
        [parent-value row-count] (cond
                                   (some? parent-value)
                                   [parent-value (count parent-value)]

                                   atleast-once?
                                   [[{}] 1]

                                   :else
                                   [nil 0])]
    [:<>
     before
     (for [i (range row-count)
           :let [value (nth parent-value i)]]
       ^{:key (str "many-" i)} ; PENDING: could use configurable key from value map
       (context/provide
        :form
        (assoc form-ctx
               :parent-value parent-value ;; Force rerender of children
               :value value
               :many-index i
               :update-attribute-fn
               (fn [field value]
                 (let [value (change-event-value value)]
                   (update-parent-fn
                    attribute
                    (assoc-in parent-value [i field] value))
                   ;; Return this fields value that goes to validation instead of update-parent-fn value
                   value)))
        body))

     after]))

(defn many
  "Vector of item field container. The body is repeated for each entry in the
  value."
  ;; FIXME: document opts
  [opts body]
  (context/consume :form [many-container opts body]))

(defn- many-remove-container [{:keys [on-remove show-if]} button-child {idx :many-index
                                                                        value :value}]
  (if (or (nil? show-if)
          (show-if value))
    (assoc-in button-child [1 :on-click]
              #(on-remove idx))
    [:span]))

(defn many-remove
  "Inside a many body, this component will render given child component
  with an on-click handler that calls on-remove with the index to remove."
  [opts button-child]
  (context/consume :form [many-remove-container opts button-child]))

(defn- update-attribute-fn
  "Return a function for updating attribute values by dispatching change events."
  [e! on-change-event]
   (fn [field value]
     (let [v (change-event-value value)]
       (e! (on-change-event
            (if (vector? field)
              (zipmap field value)
              {field v})))
       v)))

(defn form2
  "Simple context based form container."
  [{:keys [e! ;; Tuck event handle
           on-change-event ;; Input change callback
           cancel-event    ;; Form cancel callback
           cancel-fn       ;; function to call on cancel (instead of cancel-event)
           save-event      ;; Form submit callback
           value           ;; Current value of the form
           disable-buttons?
           in-progress?    ;; Submit in progess?
           spec            ;; Spec for validating form fields
           id              ;; Id for the form element
           delete          ;; Delete function
           delete-title    ;; title shown in delete confirmation dialog
           delete-message  ;; message shown in delete confirmation dialog
           delete-confirm-button-text ;; label for confirm delete button
           delete-cancel-button-text ;; label form cancel delete button
           delete-disabled-error-text ;; if specified, show delete as disabled with this text as tooltip
           delete-link? ;; if added, delete will be shown as link below other footer buttons
           autocomplete-off?]}
   & children]
  (r/with-let [invalid-attributes (r/atom #{})
               update-attribute-fn (update-attribute-fn e! on-change-event)
               current-fields (atom {})
               validate (fn [value fields]
                          (let [invalid-attrs (validate-form spec value
                                                             (vals fields))
                                valid? (and (empty? invalid-attrs)
                                            (or (nil? spec) (s/valid? spec value)))]
                            (log/info "VALIDATE invalid: " invalid-attrs " valid? " valid?
                                      (s/explain-str spec value))
                            (reset! invalid-attributes invalid-attrs)
                            valid?))
               submit! (fn [e! save-event value fields e]
                         (.preventDefault e)
                         (when (validate value fields)
                           (e! (save-event))))

               ;; Determine required fields by getting missing attributes of an empty map

               ctx {:invalid-attributes invalid-attributes
                    :update-attribute-fn update-attribute-fn
                    :validate validate
                    :submit! submit!
                    :current-fields current-fields
                    :e! e!
                    :footer {:cancel   (or cancel-fn
                                           (when cancel-event
                                             (r/partial e! (cancel-event))))
                             :validate  (when save-event
                                          (fn [value]
                                            (validate value @current-fields)))
                             :delete (when delete           ;;TODO inconsistent with save-event and cancel event
                                       #(e! delete))
                             :delete-title delete-title
                             :delete-message delete-message
                             :delete-confirm-button-text delete-confirm-button-text
                             :delete-cancel-button-text delete-cancel-button-text
                             :delete-disabled-error-text delete-disabled-error-text
                             :delete-link? delete-link?}}]
    [:form (merge {:on-submit #(submit! e! save-event value @current-fields %)
                   :style {:flex 1
                           :display :flex
                           :flex-direction :column}}
                  (when id
                    {:id id})
                  (when autocomplete-off?
                    {:auto-complete "off"}))
     (context/provide
       :form (-> ctx
                 (assoc :required-fields (missing-attributes spec {}))
                 (assoc :value value)
                 (assoc-in [:footer :cancel] (or cancel-fn
                                                 (when cancel-event
                                                   (r/partial e! (cancel-event))))) ;;This is if the cancel event is a conditional so it's not shadowed
                 (assoc-in [:footer :disabled?] (or in-progress? disable-buttons?)))
       [:<> (util/with-keys children)])]))

(defn footer2
  ([] (footer2 form-footer))
  ([footer-component]
   (context/consume
    :form
    (fn [{:keys [value footer]}]
      [footer-component (update footer :validate
                                (fn [validate]
                                  (when validate
                                    #(validate value))))]))))

(defn form
  "Simple grid based form container."
  [{:keys [class    ; CSS class for the form
           footer   ; Form footer component fn
           spacing  ; Form grid spacing
           step     ; Current form step (unused?)
           cancel-event cancel-fn
           save-event]
    :as opts
    :or {class (<class common-styles/gray-container-style)
         footer form-footer
         spacing 3}}
   & fields]
  [form2 opts
   [:div {:class class}
    [Grid {:container true
           :spacing spacing}
     (util/with-keys
       (->> fields
            (remove (partial hide-field? step))
            (map (fn [form-field]
                   (let [{:keys [xs lg md adornment container-class attribute] :as field-meta}
                         (meta form-field)]
                     (with-meta
                       [Grid (merge {:item true :xs (or xs 12)}
                                    (when lg
                                      {:lg lg})
                                    (when md
                                      {:md md}))
                        [:div {:class container-class
                               :data-form-attribute (str attribute)}
                         [field field-meta form-field]
                         (when adornment
                           adornment)]]
                       {:key (str attribute)}))))))]]
   (when (and footer
              (or cancel-fn cancel-event save-event))
     [footer2 footer])])

(defn form-modal-button
  [{:keys [form-component button-component
           modal-title
           form-value
           open? id
           max-width]
    :or {max-width "md"}}]
  (r/with-let [open-atom (r/atom (or open? false))
               form-atom (r/atom (or form-value {}))
               close #(reset! open-atom false)
               close-event (reset-atom-event open-atom false)]
    [:<>
     [panels/modal {:max-width max-width
                    :open-atom open-atom
                    :title modal-title
                    :on-close close}
      (into form-component [close-event form-atom])]
     (-> button-component
         (assoc-in [1 :id] id)
         (assoc-in [1 :on-click]
                   (fn []
                     (reset! form-atom (or form-value {}))
                     (reset! open-atom true))))]))

(defn form-container-button
  "Like form-modal-button but renders form inside the given
  container component.

  Container must be a function that can be used in hiccup as
  a component."
  [{:keys [container
           form-component button-component
           form-value
           open-atom
           open? id]}]
  (r/with-let [open-atom (or open-atom (r/atom (or open? false)))
               form-atom (r/atom (or form-value {}))
               close-event (reset-atom-event open-atom false)]
    [:<>
     (when @open-atom
       [container
        (into form-component [close-event form-atom])])
     (-> button-component
         (assoc-in [1 :id] id)
         (assoc-in [1 :on-click]
                   (fn []
                     (reset! form-atom (or form-value {}))
                     (reset! open-atom true)))
         (assoc-in [1 :disabled] @open-atom))]))
