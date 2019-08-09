(ns teet.ui.buttons
  (:require [stylefy.core :as stylefy]
            [teet.localization :refer [tr]]
                                        ;[teet.style.base :as style-base]
            [teet.ui.icons :as icons]
            [reagent.core :as r]
            [teet.ui.hotkeys :as hotkeys]
            [teet.ui.material-ui :refer [Button]]))


(defn- button-style-by-type
  [type custom-style disabled?]
  (merge
   {}
   #_(case type
      ;; common buttons, prefer these
      :primary style-base/primary-button
      :secondary style-base/secondary-button
      :tertiary style-base/tertiary-button
      :icon style-base/icon-button
      :cta style-base/cta-button
      :save style-base/cta-button
      :edit style-base/cta-button
      :search style-base/cta-button

      :cancel style-base/cancel-button
      :delete style-base/delete-button
      :add-new style-base/add-new-button
      :form-field-checkbox style-form-fields/checkbox-field
      :table-row-checkbox style-base/table-row-checkbox

      style-base/base-button)
    custom-style
    #_(when disabled?
      style-base/disabled-button)))

(defn- icon-by-type
  [type]
  (case type
    :edit icons/image-edit
    :save icons/navigation-check
    :delete icons/action-delete
    :cancel icons/navigation-cancel
    :search icons/action-search

    :add-new icons/content-add
    nil))

(defn- label-by-type
  [type]
  (case type
    :edit (tr [:buttons :edit])
    :save (tr [:buttons :save])
    :delete (tr [:buttons :delete])
    :cancel (tr [:buttons :cancel])
    :search (tr [:buttons :show-results])
    nil))

(defn format-hotkey
  [hotkey]
  (str " (" (if (= hotkey " ")
              "space"
              hotkey) ")"))

(defn button
  "Defines UI component for buttons. Try to use predefined button types whenever possible."
  [{:keys [on-click hotkey]}]
  (r/create-class
    (merge
      (when hotkey
        (hotkeys/hotkey hotkey on-click "focus-element"))
      {:reagent-render
       (fn [{:keys [id label type icon on-click custom-style wrapper-style disabled?
                    submit? button-attrs]}]
         (let [style (button-style-by-type type custom-style disabled?)
               icon (or icon (icon-by-type type))
               label (str (or label (label-by-type type))
                          (when hotkey (format-hotkey hotkey)))]
           [:div
            (stylefy/use-style (merge style-base/button-container
                                    wrapper-style))
            [Button (stylefy/use-style
                     style (merge
                            {:on-click on-click
                             :ref "focus-element"
                             :disabled (when disabled? "disabled")}
                            (when submit?
                              {:type "submit"})
                            (when id
                              {:id id})
                            (when button-attrs
                              button-attrs)))
             (if icon
               (if (= type :icon)
                 [icons/labeled-icon [icon] label :top]
                 [icons/labeled-icon [icon] label])
               label)]]))})))


(defn toggle [{:keys [on-toggle] :as opts}]
  (r/create-class
    (merge
      (when-let [hk (:hotkey opts)]
        (hotkeys/hotkey hk on-toggle "focus-toggle"))
      {:reagent-render
       (fn [{:keys [type toggle-type label on-toggle custom-style value disabled? button-attrs hotkey] :as opts}]
         (let [style (button-style-by-type type custom-style false)
               toggle-type (or toggle-type :checkbox)
               _ (assert (some #(= toggle-type %) [:radio :checkbox]) "toggle-type must be :radio or :checkbox")
               toggled-icon (if (= toggle-type :checkbox) [icons/toggle-check-box] [icons/toggle-radio-button-checked])
               not-toggled-icon (if (= toggle-type :checkbox) [icons/toggle-check-box-outline-blank] [icons/toggle-radio-button-unchecked])
               label (if (string? label)
                       (str label (when hotkey (format-hotkey hotkey)))
                       label)]
           [:button (merge
                      (stylefy/use-style (or style {})
                                       {:on-click #(do
                                                     (.stopPropagation %)
                                                     (.preventDefault %)
                                                     (on-toggle))
                                        :ref "focus-toggle"
                                        :disabled (when disabled? "disabled")})
                      {:data-checked (boolean value)}
                      (when button-attrs
                        button-attrs))
            [icons/labeled-icon (if value
                                  toggled-icon
                                  not-toggled-icon)
             label]]))})))
