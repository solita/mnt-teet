(ns teet.ui.form
  "Common container for forms"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
            [goog.object :as gobj]
            [clojure.spec.alpha :as s]
            [herb.core :refer [<class]]
            [teet.ui.buttons :as buttons]
            [teet.theme.theme-colors :as theme-colors]
            [tuck.core :as t]
            [teet.log :as log]
            [teet.ui.context :as context]
            [clojure.set :as set]))

(def default-value
  "Mapping of component to default value. Some components don't want nil as the value (like text area)."
  {TextField ""})

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

(defn form-bg
  []
  {:background-color theme-colors/gray-lightest
   :padding "1.5rem"})

(defn form-buttons
  ([] (form-buttons nil))
  ([justify]
   (merge
    (when justify
      {:justify-content justify})
    {:display :flex
     :margin-top "1.5rem"})))

(defn form-footer [{:keys [delete cancel validate disabled?]}]
  [:div {:class (<class form-buttons)}
   (when delete
     [buttons/delete-button-with-confirm {:action delete}
      (tr [:buttons :delete])])
   [:div {:style {:margin-left :auto}}
    (when cancel
      [buttons/button-secondary {:style    {:margin-right "1rem"}
                                 :disabled disabled?
                                 :on-click cancel}
       (tr [:buttons :cancel])])]
   (when validate
     [buttons/button-primary {:disabled disabled?
                              :type :submit
                              :on-click validate}
      (tr [:buttons :save])])])

(defn- hide-field?
  "Returns true if field is nil or if it has `:step` in its metadata and
  it's different from the `current-step`"
  [current-step field]
  (or (nil? field)
      (when-let [step (-> field meta :step)]
        (not= step current-step))))

(defn update-atom-event
  "Returns a tuck event that updates the given atom when processed.
  Leaves app state unaffected."
  ([the-atom] (update-atom-event the-atom (fn [_old new] new)))
  ([the-atom update-fn]
   #(reify t/Event
      (process-event [_ app]
        (swap! the-atom update-fn %)
        app))))

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
             (println "validate " field " from " before " => " after)
             after))))



(defn- field*
  [field-info _field
   {:keys [invalid-attributes required-fields current-fields] :as ctx}]

  (let [{:keys [attribute]
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
        (let [value (attribute-value value attribute
                                     (default-value (first field)))
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
                    :label (tr [:fields attribute])
                    :error error?
                    :error-text error-text
                    :required (required-field? attribute required-fields)}]
          (add-validation
           (update field 1 merge opts)
           (partial validate-attribute-fn invalid-attributes validate-field) attribute)))})))

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
           (gobj/containsKey value "target"))
    (gobj/getValueByKeys value "target" "value")
    value))

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
                    (assoc-in parent-value [i field] value)))))
        body))

     after]))

(defn many
  "Vector of item field container. The body is repeated for each entry in the
  value."
  ;; FIXME: document opts
  [opts body]
  (context/consume :form [many-container opts body]))

(defn- many-remove-container [on-remove button-child {idx :many-index}]
  (assoc-in button-child [1 :on-click]
            #(on-remove idx)))

(defn many-remove
  "Inside a many body, this component will render given child component
  with an on-click handler that calls on-remove with the index to remove."
  [on-remove button-child]
  (context/consume :form [many-remove-container on-remove button-child]))

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
           save-event      ;; Form submit callback
           value           ;; Current value of the form

           in-progress?    ;; Submit in progess?
           spec            ;; Spec for validating form fields
           id              ;; Id for the form element
           delete          ;; Delete function
           ]}
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
               required-fields (missing-attributes spec {})

               ctx {:invalid-attributes invalid-attributes
                    :update-attribute-fn update-attribute-fn
                    :validate validate
                    :submit! submit!
                    :required-fields required-fields
                    :current-fields current-fields
                    :e! e!
                    :footer {:cancel    (when cancel-event
                                          (r/partial e! (cancel-event)))
                             :validate  (when save-event
                                          (fn [value]
                                            (validate value @current-fields)))
                             :disabled? (boolean in-progress?)
                             :delete (when delete
                                       #(e! delete))}}]
    [:form (merge {:on-submit #(submit! e! save-event value @current-fields %)
                   :style {:flex 1
                           :display :flex
                           :flex-direction :column
                           :justify-content :space-between
                           :overflow :hidden}}
                  (when id
                    {:id id}))
     (context/provide
      :form (assoc ctx :value value)
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
           cancel-event
           save-event]
    :as opts
    :or {class (<class form-bg)
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
                   (let [{:keys [xs lg md adornment container-class] :as field-meta}
                         (meta form-field)]
                     [Grid (merge {:item true :xs (or xs 12)}
                                  (when lg
                                    {:lg lg})
                                  (when md
                                    {:md md}))
                      [:div {:class container-class}
                       [field field-meta form-field]
                       (when adornment
                         adornment)]])))))]]
   (when (and footer
              (or cancel-event save-event))
     [footer2 footer])])
