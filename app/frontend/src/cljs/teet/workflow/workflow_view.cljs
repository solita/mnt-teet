(ns teet.workflow.workflow-view
  (:require [teet.workflow.workflow-controller :as workflow-controller]))


(defn workflow-page
  "Main workflow page"
  [e! workflow]
  [:div (pr-str workflow)])

(defn workflow-page-and-title [e! {params :params :as app}]
  (let [id (params :workflow)
        workflow (get-in app [:workflow id])]
    {:title (:workflow/name workflow)
     :page [workflow-page e! workflow]}))
