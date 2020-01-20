(ns teet.ui.buttons
  (:require [herb.core :refer [<class]]
            [teet.ui.material-ui :refer [Button ButtonBase Link DialogActions DialogContentText]]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
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


(def button-text
  (util/make-component Button {:variant :text
                               :disable-ripple true}))

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
  [{:keys [action modal-title modal-text style class]} button-content]
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
     [button-warning {:on-click open
                      :style style
                      :class class}
      button-content]]))
