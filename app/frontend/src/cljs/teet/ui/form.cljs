(ns teet.ui.form
  "Common container for forms"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid Button]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
            [goog.object :as gobj]
            [teet.log :as log]
            [clojure.spec.alpha :as s]
            [herb.core :refer [<class]]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.buttons :as buttons]
            [teet.theme.theme-colors :as theme-colors]))


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

(defn- add-validation [field validate-attribute-fn attribute value]
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

(defn- contains-predicate [pred]
  "Check that predicate matches:
   (cljs.core/fn [%] (cljs.core/contains? % ?attr))

  Returns ?attr or nil."
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
  []
  {:text-align :right
   :margin-bottom "1.5rem"})

(defn form-footer [{:keys [cancel validate in-progress? class]}]
  (let [disabled? (boolean in-progress?)]
    [:div {:class (<class form-buttons)}
     (when cancel
       [buttons/button-secondary {:style {:margin-right "1rem"}
                                  :disabled disabled?
                                  :on-click cancel
                                  :color :secondary
                                  :variant :contained}
        (tr [:buttons :cancel])])
     (when validate
       [buttons/button-primary {:disabled disabled?
                                :color :primary
                                :variant :contained
                                :type :submit
                                :on-click validate}
        (tr [:buttons :save])])]))

(defn form
  "Simple grid based form container."
  [{:keys [e! on-change-event cancel-event save-event value in-progress? spec class]
    :or {class (<class form-bg)}}
   & fields]
  (r/with-let [invalid-attributes (r/atom #{})
               update-attribute-fn (fn [field value]
                                     (let [v (if (gobj/containsKey value "target")
                                               (gobj/getValueByKeys value "target" "value")
                                               value)]
                                       (e! (on-change-event {field v}))
                                       v))
               validate-attribute-fn (fn [field value]
                                       ;;(println "validate " field " = " value " => " (valid-attribute? field value))
                                       (swap! invalid-attributes
                                              (fn [fields]
                                                (if (valid-attribute? field value)
                                                  (disj fields field)
                                                  (conj fields field)))))
               validate (fn [value fields]
                          (let [invalid-attrs (into (missing-attributes spec value)
                                                    (for [{attr :attribute} (map meta fields)
                                                          :when (not (valid-attribute? attr (get value attr)))]
                                                      attr))
                                valid? (or (nil? spec) (s/valid? spec value))]
                            (when-not valid?
                              ;; Spec validation failed, show errors for missing or invalid attributes
                              (reset! invalid-attributes invalid-attrs))
                            valid?))
               submit! (fn [e! save-event value fields e]
                         (.preventDefault e)
                         (when (validate value fields)
                           (e! (save-event))))

               ;; Determine required fields by getting missing attributes of an empty map
               required-fields (missing-attributes spec {})]
              [:form {:on-submit #(submit! e! save-event value fields %)}
               [Grid {:container true
                      :class class}
                (util/with-keys
                  (map (fn [field]
                         (assert (vector? field) "Field must be a hiccup vector")
                         (assert (map? (second field)) "First argument to field must be an options map")
                         (let [{:keys [xs lg md attribute]} (meta field)
                               _ (assert (keyword? attribute) "All form fields must have :attribute meta key!")
                               value (get value attribute (default-value (first field)))
                               opts {:value value
                                     :on-change (r/partial update-attribute-fn attribute)
                                     :label (tr [:fields attribute])
                                     :error (boolean (@invalid-attributes attribute))
                                     :required (boolean (required-fields attribute))}]
                           [Grid (merge {:class (<class theme-spacing/mb 1)
                                         :item true :xs (or xs 12)}
                                        (when lg
                                          {:lg lg})
                                        (when md
                                          {:md md}))
                            (add-validation
                              (update field 1 merge opts)
                              validate-attribute-fn attribute value)]))
                       fields))]
               (when (or cancel-event save-event)
                 [form-footer {:cancel (r/partial e! (cancel-event))
                               :validate (when save-event
                                           #(validate value fields))
                               :in-progress? in-progress?}])]))
