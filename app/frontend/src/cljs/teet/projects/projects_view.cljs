(ns teet.projects.projects-view
  "Projects view"
  (:require [reagent.core :as r]
            [postgrest-ui.components.listing :as postgrest-listing]
            [postgrest-ui.components.filters :as postgrest-filters]
            [teet.projects.projects-controller :as projects-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.icons :as icons]
            [teet.login.login-paths :as login-paths]
            [teet.projects.project-form :as project-form]
            [postgrest-ui.components.item-view :as postgrest-item-view]))

(defmethod search-interface/format-search-result "project" [{:keys [id label]}]
  {:icon [icons/file-folder-open]
   :text label
   :href (str "#/project/" id)})

(defn- project-filter [opts]
  [postgrest-filters/simple-search-form ["searchable_text"] opts])

(defn projects-listing [e! app]
  [postgrest-listing/filtered-listing
   {:filters-view project-filter
    :endpoint (get-in app [:config :api-url])
    :token (get-in app login-paths/api-token)
    :state (get-in app [:projects :listing])
    :set-state! #(e! (projects-controller/->SetListingState %))
    :table "thk_project_search"
    :select ["id" "name" "estimated_duration" "road_nr" "km_range"]
    :drawer (fn [{:strs [id]}]
              [postgrest-item-view/item-view
               {:endpoint (get-in app [:config :api-url])
                :token (get-in app login-paths/api-token)
                :table "thk_project"
                :select ["name" "estimated_duration"
                         "road_nr" "km_range" "carriageway"
                         "procurement_no"]}
               id])}])

(defn projects-page [e! app]
  [projects-listing e! app])

(defn project-page [e! {{:keys [project]} :params :as app}]
  (if (= "new" project)
    [project-form/project-form e! (:project app)]))
