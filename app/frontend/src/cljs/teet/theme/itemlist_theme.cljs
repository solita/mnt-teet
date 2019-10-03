(ns teet.theme.itemlist-theme
  (:require [teet.theme.theme-colors :as theme-colors]
            [taoensso.timbre :as log]))

(defn heading
  [variant]
  {:margin-top (case variant
                 :primary "1rem"
                 :secondary "0.5rem")
   :margin-bottom "0.5rem"
   :display :flex
   :justify-content :space-between
   :align-items :flex-end
   :border-bottom  (case variant
                     :primary (str "3px" " solid " theme-colors/primary)
                     :secondary (str "1px solid " theme-colors/secondary))})

(defn heading-action []
  {:float :right})
