(ns teet.ui.buttons
  (:require [herb.core :refer [<class]]
            [teet.ui.material-ui :refer [Button ButtonBase Link]]
            [teet.ui.util :as util]
            [teet.theme.theme-colors :as theme-colors]))

(defn- white-button-style
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
             :focus theme-colors/focus-style}}
  {:background-color theme-colors/white
   :display :flex
   :transition "background-color 0.2s ease-in"
   :justify-content :space-between
   :border (str "1px solid " theme-colors/gray-lighter)
   :border-radius "4px"
   :font-size "1.125rem"
   :font-weight :bold
   :padding "1rem 0.5rem"
   :margin-bottom "1rem"
   :color theme-colors/primary})

(defn- warn-button-style
  "The styles are with !important because material ui css loading order makes it hard to override them normally"
  []
  ^{:pseudo {:hover {:background-color (str theme-colors/red-dark " !important")}
             :focus (str theme-colors/focus-style " !important")}}
  {:background-color (str theme-colors/error " !important")
   :color            (str theme-colors/white " !important")})


(def button-primary
  (util/make-component Button {:variant :contained
                               :disable-ripple true
                               :color :primary}))

(def button-secondary
  (util/make-component Button {:variant :contained
                               :disable-ripple true
                               :color :secondary}))

(def button-warning
  (util/make-component Button {:variant        :contained
                               :disable-ripple true
                               :class        [:btn-warn (<class warn-button-style)]}))


(defn white-button-with-icon
  [{:keys [on-click icon]} text]
  [ButtonBase {:on-click on-click
               :class (<class white-button-style)}
   text
   [icon]])

(def link-button
  (util/make-component Link {:component :button
                             :type :button}))
