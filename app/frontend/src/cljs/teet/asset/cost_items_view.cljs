(ns teet.asset.cost-items-view
  "Cost items view"
  (:require [teet.project.project-view :as project-view]
            [teet.ui.typography :as typography]
            [teet.localization :refer [tr]]))

(defn cost-items-page [e! app {:keys [fgroups project]}]
  [project-view/project-full-page-structure
   {:e! e!
    :app app
    :project project
    :left-panel [:div (tr [:project :tabs :cost-items])]
    :main [:div
           [typography/Heading2 (tr [:project :tabs :cost-items])]]}])
