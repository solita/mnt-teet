(ns teet.theme.itemlist-theme
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn heading
  []
  {:display :flex
   :justify-content :space-between
   :align-items :flex-end
   :border-bottom (str "3px solid " theme-colors/blue)})
