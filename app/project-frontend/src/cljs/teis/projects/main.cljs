(ns ^:figwheel-hooks teis.projects.main
  "TEIS project registry frontend app."
  (:require [reagent.core :as r]
            [tuck.core :as t]

            [teis.routes :as routes]
            [teis.app-state :as app-state]
            [stylefy.core :as stylefy]
            [teis.ui.panels :as panels]
            [teis.projects.project-groups.project-groups-view :as project-groups-view]
            [teis.projects.projects.projects-view :as projects-view]
            [postgrest-ui.impl.style.material]
            [postgrest-ui.elements]
            [teis.ui.material-ui :refer [Divider]]
            [datafrisk.core :as df]
            [teis.localization :as localization :refer [tr]]))

(defn groups-and-projects-page [e! app]
  [:div
   [panels/collapsible-panel {:title (tr [:project-groups :title])}
    [project-groups-view/project-groups-listing e! app]]
   [Divider]
   [panels/collapsible-panel {:title (tr [:projects :title])}
    [projects-view/projects-listing e! app]]])

(defn main-view [e! {:keys [page] :as app}]
  [:div
   (case page
     (:default-page :root :projects) [groups-and-projects-page e! app]
     :project-group [project-groups-view/project-group-page e! app])
   [df/DataFriskShell app]])

(defn ^:export main []
  (routes/start!)
  (stylefy/init)
  (postgrest-ui.elements/set-default-style! :material)
  (localization/load-initial-language!
   #(r/render [t/tuck app-state/app #'main-view]
              (.getElementById js/document "projects-app"))))

(defn ^:after-load after-load []
  (r/force-update-all))
