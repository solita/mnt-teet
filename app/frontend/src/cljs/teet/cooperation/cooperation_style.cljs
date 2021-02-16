(ns teet.cooperation.cooperation-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn application-list-item-style
  []
  ^{:pseudo {:last-child {:border-bottom (str "1px solid " theme-colors/black-coral-1)}}}
  {:padding "1.5rem 0"
   :border-top (str "1px solid " theme-colors/black-coral-1)})

(def opinion-status-color
  {:waiting-for-opinion nil
   :cooperation.opinion.status/agreed "#308653"
   :cooperation.opinion.status/partially-rejected "#FFB511"
   :cooperation.opinion.status/rejected "#D73E3E"
   :cooperation.opinion.status/unanswered theme-colors/gray-light})
