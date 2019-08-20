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
            [teet.routes :as routes]
            [teet.ui.headings :as headings]
            [teet.ui.material-ui :refer [Paper Button Chip Avatar]]
            [tuck.core :as t]
            [teet.ui.icons :as icons]))

(defn user-info [{:keys [given-name family-name] :as user}]
  (if-not user
    [Button {:color "primary"
             :href "/oauth2/request"}
     (tr [:login :login])]
    [Chip {:avatar (r/as-element [Avatar [icons/action-face]])
           :label (str given-name " " family-name)}]))

(defn main-view [e! {:keys [page user] :as app}]
  (if (= page :login)
    ;; Show only login dialog
    [login-view/login-page e! app]

    ;; Show other pages with header
    [:<>
     ;; Main header here
     [headings/header {:title "TEET projekti"
                       :action [user-info user]}]
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
               (log/info "User: " (js->clj user))
               (swap! app-state/app merge {:user user}))))

  (localization/load-initial-language!
   #(r/render [t/tuck app-state/app #'main-view]
              (.getElementById js/document "teet-frontend"))))

(defn ^:after-load after-load []
  (r/force-update-all))

(js/resolveOnload)
