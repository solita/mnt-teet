(ns teet.task.task-style
  (:require [teet.common.common-styles :as common-styles]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.responsivity-styles :as responsivity-styles]))

(defn task-status-container-style
  []
  {:display :flex
   :flex-direction :row
   :align-items :center
   :border-bottom "solid 1px"
   :border-color theme-colors/gray-light
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
  (with-meta
    (merge (common-styles/content-paper-style)
           {:display :flex
            :height "calc(100vh - 260px)"})
    (responsivity-styles/mobile-only-meta
      {:height :auto})))

(defn result-style
  []
  {:font-size "1.5rem"})

(defn study-link-style
  []
  {:display :block
   :font-size "1.5rem"
   :margin    "1.5rem 0"})

(defn file-container-style
  []
  {:margin-left "1.5rem"
   :padding-bottom "1rem"})
