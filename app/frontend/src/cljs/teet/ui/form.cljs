(ns teet.ui.form
  "Common container for forms"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
            [goog.object :as gobj]))


(defn form
  "Simple grid based form container."
  [{:keys [e! on-change-event value]} & fields]
  (r/with-let [update-attribute-fn (fn [field value]
                                     (let [v (if (gobj/containsKey value "target")
                                               (gobj/getValueByKeys value "target" "value")
                                               value)]
                                       (e! (on-change-event {field v}))))]
    [Grid {:container true}
     (util/with-keys
       (map (fn [field]
              (assert (vector? field) "Field must be a hiccup vector")
              (assert (map? (second field)) "First argument to field must be an options map")
              (let [{:keys [xs lg md attribute]} (meta field)
                    _ (assert (keyword? attribute) "All form fields must have :attribute meta key!")
                    opts {:e! e!
                          :value (get value attribute)
                          :on-change (r/partial update-attribute-fn attribute)
                          :label (tr [:fields attribute])}]
                [Grid (merge {:item true :xs (or xs 12)}
                             (when lg
                               {:lg lg})
                             (when md
                               {:md md}))
                 (update field 1 merge opts)]))
            fields))]))
