(ns ^:figwheel-hooks teet.main
  "TEET frontend app."
  (:require [datafrisk.core :as df]
            [postgrest-ui.elements]
            [postgrest-ui.impl.style.material]
            [reagent.core :as r]
            [teet.app-state :as app-state]
            [teet.localization :as localization :refer [tr]]
            [teet.login.login-view :as login-view]
            [teet.navigation.navigation-view :as navigation-view]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [CssBaseline]]
            [tuck.core :as t]
            [teet.theme.theme-provider :as theme]
            [teet.common.common-controller]

            ;; Import view namespaces
            teet.projects.projects-view
            teet.project.project-view
            teet.task.task-view
            teet.document.document-view
            teet.road-visualization.road-visualization-view
            teet.ui.component-demo

            teet.ui.query
            goog.math.Long)
  (:require-macros [teet.route-macros :refer [define-main-page]]))

;; See routes.edn
(define-main-page page-and-title)

(defn main-view [e! {:keys [page user navigation quick-search] :as app}]
  (let [nav-open? (boolean (:open? navigation))]
    [theme/theme-provider
     [:<>
      (if (= page :login)
        ;; Show only login dialog
        [login-view/login-page e! app]
        (let [{:keys [page title breadcrumbs]} (page-and-title e! app)]
          [:<>
           [CssBaseline]
           [navigation-view/header e! {:title title
                                       :open? nav-open?
                                       :breadcrumbs breadcrumbs
                                       :quick-search quick-search} user]
           [navigation-view/main-container
            nav-open?
            (with-meta page
              {:key (:route-key app)})]]))
      [df/DataFriskShell app]]]))

(defn ^:export main []
  (routes/start!)
  (postgrest-ui.elements/set-default-style! :material)

  (localization/load-initial-language!
   #(r/render [t/tuck app-state/app #'main-view]
              (.getElementById js/document "teet-frontend"))))

(defn ^:after-load after-load []
  (r/force-update-all))
