(ns ^:figwheel-hooks teet.main
  "TEET frontend app."
  (:require [datafrisk.core :as df]
            [postgrest-ui.elements]
            [postgrest-ui.impl.style.material]
            [reagent.core :as r]
            [stylefy.core :as stylefy]
            [taoensso.timbre :as log]
            [teet.app-state :as app-state]
            [teet.localization :as localization :refer [tr]]
            [teet.login.login-view :as login-view]
            [teet.projects.projects-view :as projects-view]
            [teet.project.project-view :as project-view]
            [teet.navigation.navigation-view :as navigation-view]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [Paper Button Chip Avatar MuiThemeProvider CssBaseline]]
            [teet.ui.component-demo :as component-demo]
            [tuck.core :as t]
            [teet.ui.icons :as icons]
            [teet.theme.theme-provider :as theme]
            [teet.common.common-controller]))

(defn main-view [e! {:keys [page user navigation] :as app}]
  (let [nav-open? (boolean (:open? navigation))]
    [theme/theme-provider
     (if (= page :login)
       ;; Show only login dialog
       [login-view/login-page e! app]
       [:<>
        [CssBaseline]
        [navigation-view/header e! {:title "TEET"
                                    :open? nav-open?} user]
        [navigation-view/main-container
         nav-open?
         [:<>
          (case page
            (:default-page :root :projects) [projects-view/projects-page e! app]
            :project [project-view/project-page e! app]
            :components [component-demo/demo e!]
            [:div "Unimplemented page: " (pr-str page)])]]
        [df/DataFriskShell app]])]))

(defn ^:export main []
  (routes/start!)
  (stylefy/init)
  (postgrest-ui.elements/set-default-style! :material)

  ;; Load user information
  (-> (js/fetch "/userinfo" #js {:method "POST"})
      (.then #(.json %))
      (.then (fn [user]
               (let [user (js->clj user :keywordize-keys true)]
                 (log/info "User: " user)
                 (swap! app-state/app merge {:user (when (:authenticated? user)
                                                     user)})))))

  (localization/load-initial-language!
   #(r/render [t/tuck app-state/app #'main-view]
              (.getElementById js/document "teet-frontend"))))

(defn ^:after-load after-load []
  (r/force-update-all))
