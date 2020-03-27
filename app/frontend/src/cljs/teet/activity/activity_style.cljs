(ns teet.activity.activity-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn task-row-style
  []
  ^{:pseudo {:first-of-type {:border-top "1px solid"
                             :border-color theme-colors/gray-lighter}}}
  {:display :flex
   :padding "0.5rem 1rem 0.5rem 0"
   :border-bottom "1px solid"
   :border-color theme-colors/gray-lighter})

(defn task-row-column-style
  [text-align]
  {:flex 1
   :text-align text-align})
