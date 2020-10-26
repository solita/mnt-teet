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
  {:font-weight (if seen? :normal :bold)
   :word-break :break-all})

(defn file-row-meta []
  {:font-size "90%"
   :color theme-colors/gray})

(defn file-row-style
  []
  {:display :flex
   :border-top (str "2px solid " theme-colors/black-coral-1)})

(defn file-base-column-style
  []
  {:flex 3
   :padding "0.25rem 0.25rem 0.25rem"
   :border-right (str "2px solid " theme-colors/black-coral-1)
   :display :flex})

(defn file-actions-column-style
  []
  {:padding "0.25 0 0.25rem 0.25rem"
   :display :flex
   :justify-content :center
   :align-items :flex-end
   :flex-direction :column
   :flex 1})

(defn file-comments-link-style
  [new?]
  (merge
    {}
    (when new?
      {:color theme-colors/red})))

(defn file-icon-container-style
  []
  {:flex-grow 0
   :margin-right "0.25rem"
   :padding-top "0.25rem"})

(defn file-list-entity-name-style
  []
  {:font-size "1.25rem"
   :margin-right "0.25rem"})
