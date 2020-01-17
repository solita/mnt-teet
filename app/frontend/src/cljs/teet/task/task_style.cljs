(ns teet.task.task-style
  (:require [teet.common.common-styles :as common-styles]))

(defn task-status-container-style
  []
  {:display :flex
   :flex-direction :row
   :align-items :center
   :border-bottom "solid 1px"
   :border-color teet.theme.theme-colors/gray-light
   :padding-bottom "1rem"
   :margin-bottom "1rem"})

(defn task-status-style
  []
  {:flex-basis "30%"})

(defn document-file-name []
  {:text-overflow :ellipsis
   :white-space :nowrap
   :overflow :hidden
   :display :block})

(defn task-page-paper-style
  []
  (merge (common-styles/content-paper-style)
         {:display :flex
          :flex    1}))

(defn result-style
  []
  {:font-size "1.5rem"})

(defn study-link-style
  []
  {:display :block
   :font-size "1.5rem"
   :margin    "1.5rem 0"})
