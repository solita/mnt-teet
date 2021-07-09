(ns teet.ui.vektorio-redirect-view
  (:require
   ;; link building
   [teet.common.common-controller :as common-controller]

   ;; spinner
   [teet.common.common-styles :as common-styles]
   [herb.core :as herb :refer [<class]]
   [teet.ui.material-ui :refer [CssBaseline CircularProgress]]))

(defn redirect-page [e! app state]
  (let [{:keys [project-eid vektorio-project-id]} (:params app)
        query-url (common-controller/query-url
                   :vektorio/instant-login {:db/id project-eid
                                            :vektorio/project-id vektorio-project-id })]
    (fn [e! app state]
      (.setTimeout js/window #(set! (.-location js/window) query-url) 200)
      [:div {:class (<class common-styles/spinner-style)}
       [CircularProgress]])))
