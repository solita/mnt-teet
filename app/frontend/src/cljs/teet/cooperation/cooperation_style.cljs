(ns teet.cooperation.cooperation-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn application-list-item-style
  []
  ^{:pseudo {:last-child {:border-bottom (str "1px solid " theme-colors/black-coral-1)}}}
  {:padding "1.5rem 0"
   :border-top (str "1px solid " theme-colors/black-coral-1)})
