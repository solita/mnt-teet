(ns teet.file.file-style
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn filter-sorter
  []
  {:display :flex
   :flex-direction :row
   :align-items :center
   :justify-content :space-between
   :padding "2px"
   :margin-bottom "0.25rem"})

(defn file-row-name [seen?]
  (if seen?
    {:font-weight :normal}
    {:font-weight :bold}))

(defn file-row-meta []
  {:font-size "90%"
   :color theme-colors/gray})
