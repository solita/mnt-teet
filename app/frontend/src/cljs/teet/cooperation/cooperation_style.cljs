(ns teet.cooperation.cooperation-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn application-list-item-style
  []
  ^{:pseudo {:last-child {:border-bottom (str "1px solid " theme-colors/black-coral-1)}}}
  {:padding "1.5rem 0"
   :border-top (str "1px solid " theme-colors/black-coral-1)})

(def decision-color
  {:cooperation.position.decision/agreed "#308653"
   :cooperation.position.decision/partially-rejected "#FFB511"
   :cooperation.position.decision/rejectected "#D73E3E"
   :cooperation.position.decision/unanswered theme-colors/gray-light})
