(ns teet.ui.buttons
  (:require [herb.core :refer [<class]]
            [teet.ui.material-ui :refer [Button ButtonBase]]
            [teet.ui.util :as util]
            [teet.theme.theme-colors :as theme-colors]))

(defn white-button-class
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
             :focus {:outline (str "1px solid " theme-colors/blue-lighter)}}}
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


(def button-primary
  (util/make-component Button {:variant :contained
                               :disable-ripple true
                               :color :primary}))

(defn white-button-with-icon
  [{:keys [on-click icon]} text]
  [ButtonBase {:on-click on-click
               :class (<class white-button-class)}
   text
   [icon]])
