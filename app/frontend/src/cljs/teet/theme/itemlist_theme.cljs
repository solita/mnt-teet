(ns teet.theme.itemlist-theme
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn heading
  [variant]
  {:margin-bottom "1rem"
   :display :flex
   :justify-content :space-between
   :align-items :flex-end})

(defn heading-action []
  {:float :right})
