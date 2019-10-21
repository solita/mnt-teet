(ns teet.ui.buttons
  (:require [herb.core :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [Button]]
            [teet.ui.util :as util]))


(def button-primary
  (util/make-component Button {:variant :contained
                               :disable-ripple true
                               :color :primary}))
