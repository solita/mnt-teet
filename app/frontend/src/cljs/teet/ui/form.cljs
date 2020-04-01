(ns teet.ui.form
  "Common container for forms"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.text-field :refer [TextField ]]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
            [goog.object :as gobj]
            [clojure.spec.alpha :as s]
            [herb.core :refer [<class]]
            [teet.ui.buttons :as buttons]
            [teet.theme.theme-colors :as theme-colors]
            [tuck.core :as t]
            [teet.log :as log]))

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
               validate-attribute-fn (fn [validate-field field value]
                                       (swap! invalid-attributes
                                              (fn [fields]
                                                (if (and
                                                     (or (nil? validate-field)
                                                         (nil? (validate-field value)))
                                                     (valid-attribute? field value))
                                                  (disj fields field)
                                                  (conj fields field)))))
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
                     (let [{:keys [xs lg md attribute adornment]
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
                        (add-validation
                         (update field 1 merge opts)
                         (partial validate-attribute-fn validate-field) attribute)
                        (when adornment
                          adornment)])))))]]
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
