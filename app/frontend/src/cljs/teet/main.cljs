(ns ^:figwheel-hooks teet.main
  "TEET frontend app."
  (:require [datafrisk.core :as df]
            [postgrest-ui.elements]

            [postgrest-ui.impl.style.material]
            [reagent.core :as r]
            [stylefy.core :as stylefy]
            [teet.app-state :as app-state]
            [teet.common.common-controller :as common-controller]
            [teet.localization :as localization :refer [tr]]
            [teet.project-groups.project-groups-view :as project-groups-view]
            [teet.projects.projects-view :as projects-view]
            [teet.search.search-view :as search-view]
            [teet.routes :as routes]
            [teet.ui.headings :as headings]
            [teet.ui.material-ui :refer [Divider Paper]]
            [teet.ui.panels :as panels]
            [teet.login.login-view :as login-view]
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
  (if (= page :login)
    ;; Show only login dialog
    [login-view/login-page e! app]

    ;; Show other pages with header
    [:<>
     ;; Main header here
     [headings/header {:title "TEET projekti"
                       :action [search-view/quick-search e! app]}]
     [Paper
      (case page
        (:default-page :root :projects) [groups-and-projects-page e! app]
        :project-group [project-groups-view/project-group-page e! app]
        :project [projects-view/project-page e! app]
        [:div "Unimplemented page: " (pr-str page)])]
     [df/DataFriskShell app]]))

(defn ^:export main []
  (routes/start!)
  (stylefy/init)
  (postgrest-ui.elements/set-default-style! :material)
  (localization/load-initial-language!
   #(r/render [t/tuck app-state/app #'main-view]
              (.getElementById js/document "teet-frontend"))))

(defn ^:after-load after-load []
  (r/force-update-all))

(js/resolveOnload)
