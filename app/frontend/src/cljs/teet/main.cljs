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
            [teet.navigation.navigation-view :as navigation-view]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [Paper Button Chip Avatar MuiThemeProvider]]
            [tuck.core :as t]
            [teet.ui.icons :as icons]
            [teet.ui.theme :as theme]))

(defn main-view [e! {:keys [page user navigation] :as app}]
  [theme/theme-provider
   (if (= page :login)
     ;; Show only login dialog
     [login-view/login-page e! app]
     [:div {:style {:display "flex"}}
      [navigation-view/header e! {:title "TEET projekti"
                                  :open? (:open? navigation)} user]
      [:div {:style {:flex-grow 1}}
       ;; Show other pages with header
       [:<>
        ;; Main header here
        [Paper
         ;[Button {:variant "contained" :color "primary"} "hephep"]
         (case page
           (:default-page :root :projects) [projects-view/projects-page e! app]
           :project [projects-view/project-page e! app]
           [:div "Unimplemented page: " (pr-str page)])]
        [df/DataFriskShell app]]]])])

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

(js/resolveOnload)
