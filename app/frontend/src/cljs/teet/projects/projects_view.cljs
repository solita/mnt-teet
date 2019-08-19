(ns teet.projects.projects-view
  "Projects view"
  (:require [postgrest-ui.components.listing :as postgrest-listing]
            [teet.projects.projects-controller :as projects-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.icons :as icons]
            [teet.login.login-paths :as login-paths]
            [teet.projects.project-form :as project-form]))

(defmethod search-interface/format-search-result "project" [{:keys [id label]}]
  {:icon [icons/file-folder-open]
   :text label
   :href (str "#/project/" id)})


(defn projects-listing [e! app]
  [postgrest-listing/listing
   {:endpoint (get-in app [:config :api-url])
    :token (get-in app login-paths/token)
    :state (get-in app [:projects :listing])
    :set-state! #(e! (projects-controller/->SetListingState %))
    :table "thk_project"
    :select ["id" "name" "estimated_duration" "road_nr" "km_range"]}])

(defn projects-page [e! app]
  [projects-listing e! app])

(defn project-page [e! {{:keys [project]} :params :as app}]
  (if (= "new" project)
    [project-form/project-form e! (:project app)]))
