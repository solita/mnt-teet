(ns teet.ui.num-range
  (:require [teet.common.common-styles :as common-styles]
            [teet.ui.material-ui :refer [Grid Link]]
            [herb.core :refer [<class]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.icons :as icons]))

(defn- nan? [x]
  (not (= x x)))

(defn num-range-error [error [start end] own-value min-value max-value]
  (let [v (when own-value
            (js/parseFloat own-value))]
    (or error
        (nan? v)
        (and v min-value (< v min-value))
        (and v max-value (> v max-value))
        (and start end
             (< (js/parseFloat end)
                (js/parseFloat start))))))

(defn num-range [{:keys [value on-change start-label end-label required spacing
                         reset-start reset-end
                         min-value max-value
                         error error-text]
                  :or   {spacing 3}}]
  (let [[start end] value]
    [Grid {:container true
           :spacing   spacing}
     [Grid {:item true
            :xs   6}
      [TextField (merge {:label     start-label
                         :on-change (fn [e]
                                      (on-change [(-> e .-target .-value) end]))
                         :value     start
                         ;; :type      :number
                         :step      "0.001"
                         :error     (num-range-error error value start min-value max-value)
                         :required  required}
                        (when reset-start
                          {:input-button-icon icons/av-replay
                           :input-button-click (reset-start value)}))]]
     [Grid {:item true
            :xs   6}
      [TextField (merge {:label     end-label
                         :on-change (fn [e]
                                      (on-change [start (-> e .-target .-value)]))
                         :value     end
                         ;; :type      :number
                         :step      "0.001"
                         :error     (num-range-error error value end min-value max-value)
                         :required  required}
                        (when reset-end
                          {:input-button-icon icons/av-replay
                           :input-button-click (reset-end value)}))]]
     (when (and error error-text)
       [Grid {:item true
              :xs 12}
        [:p {:class (<class common-styles/input-error-text-style)}
         error-text]])]))
