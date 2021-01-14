(ns teet.ui.buttons
  (:require [herb.core :refer [<class]]
            [teet.ui.material-ui :refer [Button ButtonBase Link DialogActions DialogContentText
                                         IconButton MenuList MenuItem
                                         ClickAwayListener Popper Paper]]
            [teet.ui.icons :as icons]
            [teet.ui.util :as util]
            [teet.localization :refer [tr]]
            [teet.theme.theme-colors :as theme-colors]
            [reagent.core :as r]
            [teet.ui.panels :as panels]
            [teet.common.common-styles :as common-styles]))

(defn- white-button-style
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
             :focus theme-colors/button-focus-style}}
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
             :focus (str theme-colors/button-focus-style " !important")
             :disabled {:background-color (str theme-colors/red-lightest " !important")}}}
  {:background-color (str theme-colors/error " !important")
   :color            (str theme-colors/white " !important")})

(defn- green-button-style
  "The styles are with !important because material ui css loading order makes it hard to override them normally"
  []
  ^{:pseudo {:hover {:background-color (str theme-colors/green-dark " !important")}
             :focus (str theme-colors/button-focus-style " !important")}}
  {:background-color (str theme-colors/green " !important")
   :color            (str theme-colors/white " !important")})

(defn- button-text-warning-style
  []
  ^{:pseudo {:focus (str theme-colors/button-focus-style " !important")
             :disabled {:background-color (str theme-colors/red-lightest " !important")}}}
  {:color (str theme-colors/red " !important")})

(defn- button-text-green-style
  []
  ^{:pseudo {:focus (str theme-colors/button-focus-style " !important")}}
  {:color (str theme-colors/green " !important")})


(def button-text
  (util/make-component Button {:variant        :text
                               :disable-ripple true}))

(def button-text-warning
  (util/make-component Button {:variant        :text
                               :disable-ripple true
                               :class          (<class button-text-warning-style)}))

(def button-text-green
  (util/make-component Button {:variant        :text
                               :disable-ripple true
                               :class          (<class button-text-green-style)}))

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

(def button-green
  (util/make-component Button {:variant        :contained
                               :disable-ripple true
                               :class          (<class green-button-style)}))

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

(defn button-with-confirm
  [{:keys [action modal-title modal-text confirm-button-text cancel-button-text close-on-action? id]}
   button-comp]
  (r/with-let [open-atom (r/atom false)
               open #(reset! open-atom true)
               close #(reset! open-atom false)]
    [:<>
     [panels/modal {:title (if modal-title
                             modal-title
                             (tr [:common :confirm-deletion]))
                    :open-atom open-atom
                    :actions [DialogActions
                              [button-secondary
                               {:on-click close
                                :id (str "confirmation-cancel")}
                               (or cancel-button-text (tr [:buttons :cancel]))]
                              [button-warning
                               {:id (str "confirmation-confirm")
                                :on-click (if close-on-action?
                                            #(do (action)
                                                 (close))
                                            action)}
                               (or confirm-button-text (tr [:buttons :delete]))]]}
      [DialogContentText
       (if modal-text
         modal-text
         (tr [:common :deletion-modal-text]))]]
     (assoc-in button-comp [1 :on-click] open)]))


(defn delete-button-with-confirm
  [{:keys [action modal-title modal-text style trashcan? small? clear? icon-position close-on-action?
           id disabled
           confirm-button-text cancel-button-text]
    :or {icon-position :end
         confirm-button-text (tr [:buttons :delete])
         cancel-button-text (tr [:buttons :cancel])
         close-on-action? true}}
   button-content]
  (r/with-let [open-atom (r/atom false)
               open #(reset! open-atom true)
               close #(reset! open-atom false)]
    [:<>
     [panels/modal {:title     (if modal-title
                                 modal-title
                                 (tr [:common :confirm-deletion]))
                    :open-atom open-atom
                    :actions   [DialogActions
                                [button-secondary
                                 {:on-click close
                                  :id (str "cancel-delete")}
                                 cancel-button-text]
                                [button-warning
                                 {:id (str "confirm-delete")
                                  :on-click (if close-on-action?
                                              #(do (action)
                                                   (close))
                                              action)}
                                 confirm-button-text]]
                    :data-cy "confirm-dialog"}
      [DialogContentText
       (if modal-text
         modal-text
         (tr [:common :deletion-modal-text]))]]
     (cond
       trashcan?
       [IconButton {:on-click open
                    :size :small}
        [icons/action-delete-outlined]
        button-content]

       clear?
       [IconButton {:on-click open
                    :size :small}
       [icons/content-clear]]

       small?
       [button-text-warning (merge {:on-click open
                                    :id id
                                    :size :small
                                    :disabled (boolean disabled)}
                                   (case icon-position
                                     :end {:end-icon (r/as-element [icons/action-delete-outline])}
                                     :start {:start-icon (r/as-element [icons/action-delete-outline])}))
        button-content]

       :else
       [button-warning {:on-click open
                        :style    style
                        :id id
                        :disabled (boolean disabled)}

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

(defn- button-with-menu-item [{:keys [label on-click]}]
  [MenuItem {:on-click on-click}
   label])

(defn button-with-menu [{:keys [on-click items]} content]
  (r/with-let [open? (r/atom false)
               anchor-el (atom nil)
               set-anchor! #(reset! anchor-el %)
               open! #(reset! open? true)
               close! #(reset! open? false)]
    [:div.button-with-menu
     [:div {:class (<class common-styles/flex-row)}
      [button-primary {:on-click on-click}
            content]
      [IconButton {:on-click open!
                   :ref set-anchor!}
       [icons/navigation-expand-more]]]
     [Popper {:open @open?
              :anchor-el @anchor-el
              :placement "bottom-start"}
      [ClickAwayListener {:on-click-away close!}
       [Paper
        [MenuList {}
         (util/mapc button-with-menu-item items)]]]]]))
