(ns ^:figwheel-hooks teet.main
  "TEET project registry frontend app."
  (:require [datafrisk.core :as df]
            [postgrest-ui.elements]

            [postgrest-ui.impl.style.material]
            [reagent.core :as r]
            [stylefy.core :as stylefy]
            [teet.app-state :as app-state]
            [teet.app-state :as app-state]
            [teet.common.common-controller :as common-controller]
            [teet.localization :as localization :refer [tr]]
            [teet.project-groups.project-groups-view :as project-groups-view]
            [teet.projects.projects-view :as projects-view]
            [teet.search.search-view :as search-view]
            [teet.routes :as routes]
            [teet.ui.headings :as headings]
            [teet.ui.material-ui :refer [Divider]]
            [teet.ui.material-ui :refer [Paper]]
            [teet.ui.panels :as panels]
            [tuck.core :as t]))

(defn groups-and-projects-page [e! app]
  [:div
   [panels/collapsible-panel {:title (tr [:project-groups :title])
                              :open-atom (common-controller/query-param-boolean-atom app :opengroups)}
    [project-groups-view/project-groups-listing e! app]]
   [Divider]
   [panels/collapsible-panel {:title (tr [:projects :title])
                              :open-atom (common-controller/query-param-boolean-atom app :openprojects)}
    [projects-view/projects-listing e! app]]])


(defn main-view [e! {:keys [page user] :as app}]
  [:div
   ;; Main header here
   [headings/header {:title "TEET projekti"
                     :action [search-view/quick-search e! app]}]
   [Paper
    (case page
      (:default-page :root :projects) [groups-and-projects-page e! app]
      :project-group [project-groups-view/project-group-page e! app]
      [:div "Unimplemented page: " (pr-str page)])]
   [df/DataFriskShell app]])

(defn ^:export main []
  (routes/start!)
  (stylefy/init)
  (postgrest-ui.elements/set-default-style! :material)
  (.then (app-state/init!)
         (fn [_]
           (localization/load-initial-language!
            #(r/render [t/tuck app-state/app #'main-view]
                       (.getElementById js/document "projects-app"))))))

(defn ^:after-load after-load []
  (r/force-update-all))
