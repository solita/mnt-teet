(ns teet.ui.form
  "Common container for forms"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid Button TextField]]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
            [goog.object :as gobj]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]))


(def default-value
  "Mapping of component to default value. Some components don't want nil as the value (like text area)."
  {TextField ""})

(defn- invalid?
  "Return true if data is invalid."
  [spec data]
  (and spec
       (not (s/valid? spec data))))

(defonce field-specs (atom {}))

(defn- valid-attribute? [kw value]
  (if-let [spec (or (@field-specs kw)
                    ((swap! field-specs assoc kw (s/get-spec kw)) kw))]
    (not= :cljs.spec.alpha/invalid (s/conform spec value))
    true))

(defn- add-validation [field validate-attribute-fn attribute value]
  (let [field-component (first field)]
    (cond
      ;; For text fields add input props to do on-blur validation
      (= TextField field-component)
      (update field 1 assoc :inputProps {:on-blur (r/partial validate-attribute-fn attribute value)})

      ;; Other fields just validate after on-change
      :else
      (update-in field [1 :on-change] (fn [on-change]
                                        (fn [value]
                                          (validate-attribute-fn attribute (on-change value))))))))

(defn form
  "Simple grid based form container."
  [{:keys [e! on-change-event cancel-event save-event value in-progress? spec]} & fields]
  (r/with-let [invalid-attributes (r/atom #{})
               update-attribute-fn (fn [field value]
                                     (let [v (if (gobj/containsKey value "target")
                                               (gobj/getValueByKeys value "target" "value")
                                               value)]
                                       (e! (on-change-event {field v}))
                                       v))
               validate-attribute-fn (fn [field value]
                                       (log/info "validate " field " = " value " => " (valid-attribute? field value))
                                       (swap! invalid-attributes
                                              (fn [fields]
                                                (if (valid-attribute? field value)
                                                  (disj fields field)
                                                  (conj fields field)))))]
    [Grid {:container true :spacing 1}
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
                          :error (boolean (@invalid-attributes attribute))}]
                [Grid (merge {:item true :xs (or xs 12)}
                             (when lg
                               {:lg lg})
                             (when md
                               {:md md}))
                 (add-validation
                  (update field 1 merge opts)
                  validate-attribute-fn attribute value)]))
            fields))
     (when (or cancel-event save-event)
       (let [save-disabled? (boolean (or in-progress?
                                         (invalid? spec value)))]
         [Grid {:item true :xs 12 :align "right"}
          (when cancel-event
            [Button {:disabled (boolean in-progress?)
                     :on-click (r/partial e! (cancel-event))
                     :color "secondary"
                     :variant "outlined"}
             (tr [:buttons :cancel])])
          (when save-event
            [Button {:disabled save-disabled?
                     :on-click (r/partial e! (save-event))
                     :color "primary"
                     :variant "outlined"}
             (tr [:buttons :save])])]))]))
