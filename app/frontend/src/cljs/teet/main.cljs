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
            [tuck.core :as t]
            [taoensso.timbre :as log]))


(defn main-view [e! {:keys [page user] :as app}]
  (if (= page :login)
    ;; Show only login dialog
    [login-view/login-page e! app]

    ;; Show other pages with header
    [:<>
     ;; Main header here
     [headings/header {:title "TEET projekti"
                       ;:action [search-view/quick-search e! app]
                       }]
     [Paper
      (case page
        (:default-page :root :projects) [projects-view/projects-page e! app]
        :project [projects-view/project-page e! app]
        [:div "Unimplemented page: " (pr-str page)])]
     [df/DataFriskShell app]]))

(defn ^:export main []
  (routes/start!)
  (stylefy/init)
  (postgrest-ui.elements/set-default-style! :material)

  ;; Load user information
  (-> (js/fetch "/userinfo" #js {:method "POST"})
      (.then #(.json %))
      (.then (fn [user]
               (log/info "User: " user)
               (swap! app-state/app merge :user user))))

  (localization/load-initial-language!
   #(r/render [t/tuck app-state/app #'main-view]
              (.getElementById js/document "teet-frontend"))))

(defn ^:after-load after-load []
  (r/force-update-all))

(js/resolveOnload)
