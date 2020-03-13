(ns teet.ui.buttons
  (:require [herb.core :refer [<class]]
            [teet.ui.material-ui :refer [Button ButtonBase Link DialogActions DialogContentText]]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
            [teet.ui.icons :as icons]
            [teet.theme.theme-colors :as theme-colors]
            [reagent.core :as r]
            [teet.ui.panels :as panels]))

(defn- white-button-style
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
             :focus theme-colors/focus-style}}
  {:background-color theme-colors/white
   :display          :flex
   :transition       "background-color 0.2s ease-in"
   :justify-content  :space-between
   :border           (str "1px solid " theme-colors/gray-lighter)
   :border-radius    "4px"
   :font-size        "1.125rem"
   :font-weight      :bold
   :padding          "1rem 0.5rem"
   :margin-bottom    "1rem"
   :color            theme-colors/primary})

(defn- warn-button-style
  "The styles are with !important because material ui css loading order makes it hard to override them normally"
  []
  ^{:pseudo {:hover {:background-color (str theme-colors/red-dark " !important")}
             :focus (str theme-colors/focus-style " !important")}}
  {:background-color (str theme-colors/error " !important")
   :color            (str theme-colors/white " !important")})

(defn- button-text-warning-style
  []
  ^{:pseudo {:focus (str theme-colors/focus-style " !important")}}
  {:color (str theme-colors/red " !important")})


(def button-text
  (util/make-component Button {:variant        :text
                               :disable-ripple true}))

(def button-text-warning
  (util/make-component Button {:variant        :text
                               :disable-ripple true
                               :class          (<class button-text-warning-style)}))

(def button-primary
  (util/make-component Button {:variant        :contained
                               :disable-ripple true
                               :color          :primary}))

(def button-secondary
  (util/make-component Button {:variant        :contained
                               :disable-ripple true
                               :color          :secondary}))

(def button-warning
  (util/make-component Button {:variant        :contained
                               :disable-ripple true
                               :class          (<class warn-button-style)}))

(def rect-primary
  (util/make-component Button {:variant        :outlined
                               :color          :primary
                               :disable-ripple true}))

(def rect-white
  (util/make-component Button {:variant        :outlined
                               :color          :default
                               :disable-ripple true}))

(defn white-button-with-icon
  [{:keys [on-click icon]} text]
  [ButtonBase {:on-click on-click
               :class    (<class white-button-style)}
   text
   [icon]])

(def link-button
  (util/make-component Link {:component :button
                             :type      :button}))

(defn delete-button-with-confirm
  [{:keys [action modal-title modal-text style small?]} button-content]
  (let [open-atom (r/atom false)
        open #(reset! open-atom true)
        close #(reset! open-atom false)]
    [:<>
     [panels/modal {:title     (if modal-title
                                 modal-title
                                 (tr [:common :confirm-deletion]))
                    :open-atom open-atom
                    :actions   [DialogActions
                                [button-secondary
                                 {:on-click close}
                                 (tr [:buttons :cancel])]
                                [button-warning
                                 {:on-click action}
                                 (tr [:buttons :delete])]]}
      [DialogContentText
       (if modal-text
         modal-text
         (tr [:common :deletion-modal-text]))]]
     (if small?
       [button-text-warning {:on-click open
                             :size :small
                             :end-icon (r/as-element [icons/action-delete-outline])}
        button-content]
       [button-warning {:on-click open
                        :style    style}
        button-content])]))

(defn- add-button-style
  [disabled?]
  (with-meta
    (merge {:border "1px solid black"
            :transition "background-color 0.2s ease-in-out"
            :display :flex
            :width "100%"
            :height "42px"
            :justify-content :center
            :padding "0.5rem"}
           (when disabled?
             {:background-color teet.theme.theme-colors/gray-light}))
    (when (not disabled?)
      {:pseudo {:hover {:background-color theme-colors/gray-lightest}
                :active {:background-color theme-colors/gray-light}}})))

(defn add-button
  [{:keys [on-click disabled?]} label]
  [ButtonBase {:on-click on-click
               :disable-ripple true
               :disabled disabled?
               :class (<class add-button-style disabled?)}
   label])

