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
                                          (println "VALIDATE ATRRIBUTE : " validate-attribute-fn)
                                          (.log js/console "validate attribute: " validate-attribute-fn)
                                          (println "value : " value)
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
  "Return missing attribuets based on spec"
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
          (map (fn [{:keys [attribute validate]}]
                 (validate-attribute
                  validate
                  attribute
                  (if (vector? attribute)
                    ((apply juxt attribute) value)
                    (get value attribute))))
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

(defn form
  "Simple grid based form container."
  [{:keys [e! ;; Tuck event handle
           on-change-event ;; Input change callback
           cancel-event    ;; Form cancel callback
           save-event      ;; Form submit callback
           value           ;; Current value of the form

           in-progress?    ;; Submit in progess?
           spec            ;; Spec for validating form fields
           class           ;; CSS class for the form
           footer          ;; Form footer component fn
           spacing         ;; Form grid spacing
           step            ;; Current form step
           id              ;; Id for the form element
           delete          ;; Delete function
           ]
    :or {class (<class form-bg)
         footer form-footer
         spacing 3}}
   & fields]
  (r/with-let [invalid-attributes (r/atom #{})
               update-attribute-fn (fn [field value]
                                     (let [v (if (and (not (boolean? value))
                                                      (gobj/containsKey value "target"))
                                               (gobj/getValueByKeys value "target" "value")
                                               value)]
                                       (e! (on-change-event
                                            (if (vector? field)
                                              (zipmap field value)
                                              {field v})))
                                       v))
               validate (fn [value fields]
                          (let [invalid-attrs (into (missing-attributes spec value)
                                                    (for [{attr :attribute
                                                           validate-field :validate} (map meta fields)
                                                          :let [validation-error
                                                                (and validate-field
                                                                     (validate-field (get value attr)))]
                                                          :when (or validation-error
                                                                    (not (valid-attribute? attr (get value attr))))]
                                                      attr))
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
               _ (log/debug "required-fields:" required-fields)]
    [:form (merge {:on-submit #(submit! e! save-event value fields %)
                   :style {:flex 1
                           :display :flex
                           :flex-direction :column
                           :justify-content :space-between
                           :overflow :hidden}}
                  (when id
                    {:id id}))
     [:div {:class class}
      [Grid {:container true
             :spacing spacing}
       (util/with-keys
         (->> fields
              (remove (partial hide-field? step))
              (map (fn [field]
                     (assert (vector? field) "Field must be a hiccup vector")
                     (assert (map? (second field)) "First argument to field must be an options map")
                     (let [{:keys [xs lg md attribute adornment container-class]
                            validate-field :validate :as field-meta} (meta field)
                           value (cond
                                   (keyword? attribute)
                                   (get value attribute (default-value (first field)))

                                   (vector? attribute)
                                   (mapv #(get value % (default-value (first field))) attribute)

                                   :else
                                   (throw (ex-info "All form fields must have :attribute meta key (keyword or vector of keywords)"
                                                   {:meta field-meta})))
                           error-text (and validate-field
                                           (validate-field value))
                           opts {:value value
                                 :on-change (r/partial update-attribute-fn attribute)
                                 :label (tr [:fields attribute])
                                 :error (boolean (or error-text (@invalid-attributes attribute)))
                                 :error-text error-text
                                 :required (required-field? attribute required-fields)}
                           ;; _ (log/debug "determining required-ness for" attribute " - " required-fields "says" (boolean (required-fields attribute)))
                           ]
                       [Grid (merge {:item true :xs (or xs 12)}
                                    (when lg
                                      {:lg lg})
                                    (when md
                                      {:md md}))
                        [:div {:class container-class}
                         (add-validation
                           (update field 1 merge opts)
                           (partial validate-attribute-fn invalid-attributes validate-field) attribute)
                         (when adornment
                           adornment)]])))))]]
     (when (and footer
                (or cancel-event save-event))
       [footer (merge
                 {:cancel    (when cancel-event
                               (r/partial e! (cancel-event)))
                  :validate  (when save-event
                               #(validate value fields))
                  :disabled? (boolean in-progress?)}
                 (when delete
                   {:delete #(e! delete)}))])]))

(defn- field*
  [field-info field
   {:keys [value update-attribute-fn invalid-attributes required-fields current-fields]}]
  (let [{:keys [attribute]
         validate-field :validate
         :as field-info} (if (map? field-info)
                           field-info
                           {:attribute field-info})]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (log/info "form field for " attribute " mounted")
        (swap! current-fields assoc attribute field-info))
      :component-will-unmount
      (fn [_]
        (swap! current-fields dissoc attribute))
      :reagent-render
      (fn [_ _ {:keys [value]}]
        (let [value (cond
                      (keyword? attribute)
                      (get value attribute (default-value (first field)))

                      (vector? attribute)
                      (mapv #(get value % (default-value (first field))) attribute)

                      :else
                      (throw (ex-info "All form fields must have :attribute key (keyword or vector of keywords)"
                                      {:meta field-info})))
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
               update-attribute-fn (fn [field value]
                                     (let [v (if (and (not (boolean? value))
                                                      (gobj/containsKey value "target"))
                                               (gobj/getValueByKeys value "target" "value")
                                               value)]
                                       (e! (on-change-event
                                            (if (vector? field)
                                              (zipmap field value)
                                              {field v})))
                                       v))
               current-fields (atom {})
               validate (fn [value fields]
                          (let [invalid-attrs (validate-form spec value fields)
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

(defn footer2 []
  (context/consume
   :form
   (fn [{:keys [value footer]}]
     [form-footer (update footer :validate
                          (fn [validate]
                            (when validate
                              #(validate value))))])))
