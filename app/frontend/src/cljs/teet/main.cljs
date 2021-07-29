(ns ^:figwheel-hooks teet.main
  "TEET frontend app."
  (:require react
            react-dom
            [herb.core :as herb :refer [<class]]
            [datafrisk.core :as df]
            [postgrest-ui.elements]
            [postgrest-ui.impl.style.material]
            reagent.dom
            [teet.app-state :as app-state]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.localization :as localization]
            [teet.log :as log]
            [teet.login.login-view :as login-view]
            [teet.navigation.navigation-view :as navigation-view]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [CssBaseline CircularProgress]]
            [teet.ui.build-info :as build-info]
            [teet.ui.drag :as drag]
            [tuck.core :as t]
            [teet.theme.theme-provider :as theme]
            [teet.snackbar.snackbar-view :as snackbar]
            [teet.common.common-controller :refer [when-feature poll-version] :as common-controller]

            ;; Import view namespaces and needed utility namespaces (macroexpansion)
            teet.projects.projects-view
            teet.project.project-view
            teet.project.project-model
            teet.task.task-view
            teet.road-visualization.road-visualization-view
            teet.ui.component-demo
            teet.admin.admin-view
            teet.dashboard.dashboard-view
            teet.activity.activity-view
            teet.file.file-view
            teet.ui.unauthorized
            teet.meeting.meeting-view
            teet.cooperation.cooperation-view
            teet.asset.asset-library-view
            teet.account.account-view
            teet.asset.cost-groups-view
            teet.asset.cost-items-view
            teet.asset.materials-and-products-view
            teet.contract.contracts-view
            teet.contract.contract-view
            teet.contract.contract-partners-view
            teet.contract.contract-responsibilities-view
            teet.asset.assets-view
            teet.ui.vektorio-redirect-view

            teet.ui.query
            [teet.ui.url :as url]

            ;; Required by define-main-page which uses string->long
            [teet.login.login-controller :as login-controller]
            [teet.common.common-styles :as common-styles]
            [teet.ui.context :as context])
  (:require-macros [teet.route-macros :refer [define-main-page]]))

;; See routes.edn
(def ->long common-controller/->long)
(define-main-page page-and-title)

(defn- main-view-content [e! nav-open? app]
  (if (get-in app [:config :api-url])               ;;config gets loaded when session is checked
    (let [{:keys [page]} (page-and-title e! app)]      
      [:<>
       [drag/drag-handler e!]
       [navigation-view/header e! app
        {:open? nav-open?
         :page (:page app)
         :quick-search (:quick-search app)
         :url (:url app)
         :extra-panel (get-in app [:navigation :extra-panel])
         :extra-panel-open? (get-in app [:navigation :extra-panel-open?])}
        (:user app)]
       [navigation-view/main-container
        nav-open?
        (with-meta page
          {:key (:route-key app)})]])
    ;; else - show spinner while config is loaded
    [:div {:class (<class common-styles/spinner-style)}
     [CircularProgress]]))

(defn main-view [e! _]
  (log/hook-onerror! e!)
  (poll-version e!)
  (e! (login-controller/->CheckExistingSession))
  (fn [e! {:keys [page navigation snackbar] :as app}]
    (let [nav-open? (boolean (:open? navigation))]
      [url/provide-navigation-info
       (select-keys app [:page :params :query])
       [theme/theme-provider
        [:div {:style {:display        :flex
                       :flex-direction :column
                       :min-height     "100vh"}}
         [build-info/top-banner nav-open? page]
         [snackbar/snackbar-container e! snackbar]
         [CssBaseline]
         (if (= page :login)
           ;; Show only login dialog
           [:div {:style {:display :flex
                          :min-height "100vh"
                          :flex-direction :column}}
            [navigation-view/login-header e!
             {:url (:url app)
              :extra-panel (get-in app [:navigation :extra-panel])
              :extra-panel-open? (get-in app [:navigation :extra-panel-open?])}]
              [login-view/login-page e! app]]
           ;; else - show routing determined component surrounded by common containers
           [context/provide :user (:user app)
            [main-view-content e! nav-open? app]])
         (when-feature :data-frisk
           [df/DataFriskShell app])]]])))

(defn ^:export main []
  (routes/start!)
  (postgrest-ui.elements/set-default-style! :material)
  (localization/load-initial-language!
   #(reagent.dom/render [t/tuck app-state/app #'main-view]
                        (.getElementById js/document "teet-frontend"))))

(defn ^:after-load after-load []
  (reagent.dom/force-update-all))
