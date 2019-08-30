(ns teet.workflow.workflow-view
  (:require [teet.workflow.workflow-controller :as workflow-controller]))

(defn workflow-page
  "Main workflow page"
  [e! workflow]
  [:div (pr-str workflow)])
